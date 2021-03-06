package com.igeak.record;

/**
 * Created by xuqiang on 16-7-2.
 */

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.Animator.AnimatorListener;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.PointF;
import android.os.Handler;
import android.support.v7.widget.LinearSmoothScroller;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.view.SimpleAnimatorListener;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Property;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.Interpolator;
import android.widget.Scroller;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@TargetApi(20)
public class WearableListView extends RecyclerView {
    private static final String TAG = "WearableListView";
    private static final long FLIP_ANIMATION_DURATION_MS = 150L;
    private static final long CENTERING_ANIMATION_DURATION_MS = 150L;
    private static final float TOP_TAP_REGION_PERCENTAGE = 0.33F;
    private static final float BOTTOM_TAP_REGION_PERCENTAGE = 0.33F;
    private static final int THIRD = 3;
    private final int mMinFlingVelocity;
    private final int mMaxFlingVelocity;
    private boolean mMaximizeSingleItem;
    private boolean mCanClick;
    private boolean mGestureNavigationEnabled;
    private int mTapPositionX;
    private int mTapPositionY;
    private WearableListView.ClickListener mClickListener;
    private Animator mScrollAnimator;
    private int mLastScrollChange;
    private WearableListView.SetScrollVerticallyProperty mSetScrollVerticallyProperty;
    private final List<WearableListView.OnScrollListener> mOnScrollListeners;
    private final List<WearableListView.OnCentralPositionChangedListener>
            mOnCentralPositionChangedListeners;
    private WearableListView.OnOverScrollListener mOverScrollListener;
    private boolean mGreedyTouchMode;
    private float mStartX;
    private float mStartY;
    private float mStartFirstTop;
    private final int mTouchSlop;
    private boolean mPossibleVerticalSwipe;
    private int mInitialOffset;
    private Scroller mScroller;
    private final float[] mTapRegions;
    private boolean mGestureDirectionLocked;
    private int mPreviousCentral;
    private final int[] mLocation;
    private View mPressedView;
    private final Runnable mPressedRunnable;
    private final Runnable mReleasedRunnable;
    private Runnable mNotifyChildrenPostLayoutRunnable;
    private final WearableListView.OnChangeObserver mObserver;
    private boolean isScroll = false;


    public WearableListView(Context context) {
        this(context, (AttributeSet) null);
    }

    public WearableListView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WearableListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.mCanClick = true;
        this.mGestureNavigationEnabled = true;
        this.mSetScrollVerticallyProperty = new WearableListView.SetScrollVerticallyProperty();
        this.mOnScrollListeners = new ArrayList();
        this.mOnCentralPositionChangedListeners = new ArrayList();
        this.mInitialOffset = 0;
        this.mTapRegions = new float[2];
        this.mPreviousCentral = 0;
        this.mLocation = new int[2];
        this.mPressedView = null;
        this.mPressedRunnable = new Runnable() {
            public void run() {
                if (WearableListView.this.getChildCount() > 0) {
                    WearableListView.this.mPressedView = WearableListView.this.getChildAt
                            (WearableListView.this.findCenterViewIndex());
                    WearableListView.this.mPressedView.setPressed(true);
                } else {
                    Log.w("WearableListView", "mPressedRunnable: the children were removed, " +
                            "skipping.");
                }

            }
        };
        this.mReleasedRunnable = new Runnable() {
            public void run() {
                WearableListView.this.releasePressedItem();
            }
        };
        this.mNotifyChildrenPostLayoutRunnable = new Runnable() {
            public void run() {
                WearableListView.this.notifyChildrenAboutProximity(false);
            }
        };
        this.mObserver = new WearableListView.OnChangeObserver();
        this.setHasFixedSize(true);
        this.setOverScrollMode(2);
        this.setLayoutManager(new WearableListView.LayoutManager());
        android.support.v7.widget.RecyclerView.OnScrollListener onScrollListener = new android
                .support.v7.widget.RecyclerView.OnScrollListener() {
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == SCROLL_STATE_IDLE) {
                    WearableListView.this.onScrollStop();
                }

                if (newState == SCROLL_STATE_DRAGGING) {
                    WearableListView.this.onScrollStart();
                }
                if (newState == 0 && WearableListView.this.getChildCount() > 0) {
                    WearableListView.this.handleTouchUp((MotionEvent) null, newState);
                }

                Iterator var3 = WearableListView.this.mOnScrollListeners.iterator();

                while (var3.hasNext()) {
                    WearableListView.OnScrollListener listener = (WearableListView
                            .OnScrollListener) var3.next();
                    listener.onScrollStateChanged(newState);
                }


            }

            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                //Log.i("xerrard", "RecyclerView onScrolled" + "  dy = " + dy);
                WearableListView.this.onScroll(dy);
            }
        };
        this.setOnScrollListener(onScrollListener);
        ViewConfiguration vc = ViewConfiguration.get(context);
        this.mTouchSlop = vc.getScaledTouchSlop();
        this.mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
        this.mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
    }

    public void setAdapter(android.support.v7.widget.RecyclerView.Adapter adapter) {
        this.mObserver.setAdapter(adapter);
        super.setAdapter(adapter);
    }

    public int getBaseline() {
        if (this.getChildCount() == 0) {
            return super.getBaseline();
        } else {
            int centerChildIndex = this.findCenterViewIndex();
            int centerChildBaseline = this.getChildAt(centerChildIndex).getBaseline();
            return centerChildBaseline == -1 ? super.getBaseline() : this.getCentralViewTop() +
                    centerChildBaseline;
        }
    }

    public boolean isAtTop() {
        if (this.getChildCount() == 0) {
            return true;
        } else {
            int centerChildIndex = this.findCenterViewIndex();
            View centerView = this.getChildAt(centerChildIndex);
            return this.getChildAdapterPosition(centerView) == 0 && this.getScrollState() == 0;
        }
    }

    public void resetLayoutManager() {
        this.setLayoutManager(new WearableListView.LayoutManager());
    }

    public void setGreedyTouchMode(boolean greedy) {
        this.mGreedyTouchMode = greedy;
    }

    public void setInitialOffset(int top) {
        this.mInitialOffset = top;
    }

    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (!this.isEnabled()) {
            return false;
        } else {
            if (this.mGreedyTouchMode && this.getChildCount() > 0) {
                int action = event.getActionMasked();
                if (action == 0) {
                    this.mStartX = event.getX();
                    this.mStartY = event.getY();
                    this.mStartFirstTop = this.getChildCount() > 0 ? (float) this.getChildAt(0)
                            .getTop() : 0.0F;
                    this.mPossibleVerticalSwipe = true;
                    this.mGestureDirectionLocked = false;
                } else if (action == 2 && this.mPossibleVerticalSwipe) {
                    this.handlePossibleVerticalSwipe(event);
                }

                this.getParent().requestDisallowInterceptTouchEvent(this.mPossibleVerticalSwipe);
            }

            return super.onInterceptTouchEvent(event);
        }
    }

    private boolean handlePossibleVerticalSwipe(MotionEvent event) {
        if (this.mGestureDirectionLocked) {
            return this.mPossibleVerticalSwipe;
        } else {
            float deltaX = Math.abs(this.mStartX - event.getX());
            float deltaY = Math.abs(this.mStartY - event.getY());
            float distance = deltaX * deltaX + deltaY * deltaY;
            if (distance > (float) (this.mTouchSlop * this.mTouchSlop)) {
                if (deltaX > deltaY) {
                    this.mPossibleVerticalSwipe = false;
                }

                this.mGestureDirectionLocked = true;
            }

            return this.mPossibleVerticalSwipe;
        }
    }


    public boolean onTouchEvent(MotionEvent event) {
        if (!this.isEnabled()) {
            return false;
        } else {
            int scrollState = this.getScrollState();
            boolean result = super.onTouchEvent(event);
            if (this.getChildCount() > 0) {
                int action = event.getActionMasked();
                if (action == 0) {

                    this.handleTouchDown(event);
                } else if (action == 1) {

                    this.handleTouchUp(event, scrollState);
                    this.getParent().requestDisallowInterceptTouchEvent(false);
                    WearableListView.this.onTouchUp(); //xerrard
                } else if (action == 2) {

                    if (Math.abs(this.mTapPositionY - (int) event.getY()) >= this.mTouchSlop*2) {
                        WearableListView.this.onTouchDown();
                    }  //xerrard

                    if (Math.abs(this.mTapPositionX - (int) event.getX()) >= this.mTouchSlop ||
                            Math.abs(this.mTapPositionY - (int) event.getY()) >= this.mTouchSlop) {

                        this.releasePressedItem();
                        this.mCanClick = false;
                    }

                    result |= this.handlePossibleVerticalSwipe(event);
                    this.getParent().requestDisallowInterceptTouchEvent(this
                            .mPossibleVerticalSwipe);
                } else if (action == 3) {
                    this.getParent().requestDisallowInterceptTouchEvent(false);
                    this.mCanClick = true;
                }
            }

            return result;
        }
    }

    private void releasePressedItem() {
        if (this.mPressedView != null) {
            this.mPressedView.setPressed(false);
            this.mPressedView = null;
        }

        Handler handler = this.getHandler();
        if (handler != null) {
            handler.removeCallbacks(this.mPressedRunnable);
        }

    }

    private void onScroll(int dy) {
        //Log.i("xerrard","view onScroll");
        isScroll = true;
        Iterator var2 = this.mOnScrollListeners.iterator();

        while (var2.hasNext()) {
            WearableListView.OnScrollListener listener = (WearableListView.OnScrollListener) var2
                    .next();
            listener.onScroll(dy);
        }
        this.notifyChildrenAboutProximity(true);


    }

    private void onTouchDown() {
        this.onAllItemTouch(true);
    }

    private void onTouchUp() {

        this.onAllItemTouch(false);

    }

    private void onScrollStart() {
        //Log.i("xerrard","view onScrollStart");
        this.onAllItemScroll(true);
    }


    private void onScrollStop() {
        //Log.i("xerrard","view onScrollStoped");
        this.onAllItemScroll(false);

    }

    private void onAllItemScroll(boolean isScroll) {
        WearableListView.LayoutManager layoutManager = (WearableListView.LayoutManager) this
                .getLayoutManager();
        int count = layoutManager.getChildCount();
        if (count != 0) {
            for (int position = 0; position < count; ++position) {
                View view = layoutManager.getChildAt(position);
                WearableListView.ViewHolder listener = this.getChildViewHolder(view);
                listener.onItemScroll(isScroll);
            }
        }
    }


    private void onAllItemTouch(boolean isDown) {
        WearableListView.LayoutManager layoutManager = (WearableListView.LayoutManager) this
                .getLayoutManager();
        int count = layoutManager.getChildCount();
        if (count != 0) {
            for (int position = 0; position < count; ++position) {
                View view = layoutManager.getChildAt(position);
                WearableListView.ViewHolder listener = this.getChildViewHolder(view);
                listener.onItemTouch(isDown);
            }
        }
    }


    public void addOnScrollListener(WearableListView.OnScrollListener listener) {
        this.mOnScrollListeners.add(listener);
    }

    public void removeOnScrollListener(WearableListView.OnScrollListener listener) {
        this.mOnScrollListeners.remove(listener);
    }

    public void addOnCentralPositionChangedListener(WearableListView
                                                            .OnCentralPositionChangedListener
                                                            listener) {
        this.mOnCentralPositionChangedListeners.add(listener);
    }

    public void removeOnCentralPositionChangedListener(WearableListView
                                                               .OnCentralPositionChangedListener
                                                               listener) {
        this.mOnCentralPositionChangedListeners.remove(listener);
    }

    public boolean isGestureNavigationEnabled() {
        return this.mGestureNavigationEnabled;
    }

    public void setEnableGestureNavigation(boolean enabled) {
        this.mGestureNavigationEnabled = enabled;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (this.mGestureNavigationEnabled) {
            switch (keyCode) {
                case 260:
                    this.fling(0, -this.mMinFlingVelocity);
                    return true;
                case 261:
                    this.fling(0, this.mMinFlingVelocity);
                    return true;
                case 262:
                    return this.tapCenterView();
                case 263:
                    return false;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean tapCenterView() {
        if (this.isEnabled() && this.getVisibility() == 0 && this.getChildCount() >= 1) {
            int index = this.findCenterViewIndex();
            View view = this.getChildAt(index);
            WearableListView.ViewHolder holder = this.getChildViewHolder(view);
            if (view.performClick()) {
                return true;
            } else if (this.mClickListener != null) {
                this.mClickListener.onClick(holder);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean checkForTap(MotionEvent event) {
        if (!this.isEnabled()) {
            return false;
        } else {
            float rawY = event.getRawY();
            int index = this.findCenterViewIndex();
            View view = this.getChildAt(index);
            WearableListView.ViewHolder holder = this.getChildViewHolder(view);
            this.computeTapRegions(this.mTapRegions);
            if (rawY > this.mTapRegions[0] && rawY < this.mTapRegions[1]) {
                if (this.mClickListener != null) {
                    this.mClickListener.onClick(holder);
                }

                return true;
            } else if (index > 0 && rawY <= this.mTapRegions[0]) {
                this.animateToMiddle(index - 1, index);
                return true;
            } else if (index < this.getChildCount() - 1 && rawY >= this.mTapRegions[1]) {
                this.animateToMiddle(index + 1, index);
                return true;
            } else if (index == 0 && rawY <= this.mTapRegions[0] && this.mClickListener != null) {
                this.mClickListener.onTopEmptyRegionClick();
                return true;
            } else {
                return false;
            }
        }
    }

    private void animateToMiddle(int newCenterIndex, int oldCenterIndex) {
        if (newCenterIndex == oldCenterIndex) {
            throw new IllegalArgumentException("newCenterIndex must be different from " +
                    "oldCenterIndex");
        } else {
            ArrayList animators = new ArrayList();
            View child = this.getChildAt(newCenterIndex);
            int scrollToMiddle = this.getCentralViewTop() - child.getTop();
            this.startScrollAnimation(animators, scrollToMiddle, 150L);
        }
    }

    private void startScrollAnimation(List<Animator> animators, int scroll, long duration) {
        this.startScrollAnimation(animators, scroll, duration, 0L);
    }

    private void startScrollAnimation(List<Animator> animators, int scroll, long duration, long
            delay) {
        this.startScrollAnimation(animators, scroll, duration, delay, (AnimatorListener) null);
    }

    private void startScrollAnimation(int scroll, long duration, long delay, AnimatorListener
            listener) {
        this.startScrollAnimation((List) null, scroll, duration, delay, listener);
    }

    private void startScrollAnimation(List<Animator> animators, int scroll, long duration, long
            delay, AnimatorListener listener) {
        if (this.mScrollAnimator != null) {
            this.mScrollAnimator.cancel();
        }

        this.mLastScrollChange = 0;
        ObjectAnimator scrollAnimator = ObjectAnimator.ofInt(this, this
                .mSetScrollVerticallyProperty, new int[]{0, -scroll});
        if (animators != null) {
            animators.add(scrollAnimator);
            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(animators);
            this.mScrollAnimator = animatorSet;
        } else {
            this.mScrollAnimator = scrollAnimator;
        }

        this.mScrollAnimator.setDuration(duration);
        if (listener != null) {
            this.mScrollAnimator.addListener(listener);
        }

        if (delay > 0L) {
            this.mScrollAnimator.setStartDelay(delay);
        }

        this.mScrollAnimator.start();
    }

    public boolean fling(int velocityX, int velocityY) {
        if (this.getChildCount() == 0) {
            return false;
        } else {
            int index = this.findCenterViewIndex();
            View child = this.getChildAt(index);
            int currentPosition = this.getChildPosition(child);
            if ((currentPosition != 0 || velocityY >= 0) && (currentPosition != this.getAdapter()
                    .getItemCount() - 1 || velocityY <= 0)) {
                if (Math.abs(velocityY) < this.mMinFlingVelocity) {
                    return false;
                } else {
                    velocityY = Math.max(Math.min(velocityY, this.mMaxFlingVelocity), -this
                            .mMaxFlingVelocity);
                    if (this.mScroller == null) {
                        this.mScroller = new Scroller(this.getContext(), (Interpolator) null, true);
                    }

                    this.mScroller.fling(0, 0, 0, velocityY, -2147483648, 2147483647,
                            -2147483648, 2147483647);
                    int finalY = this.mScroller.getFinalY();
                    int delta = finalY / (this.getPaddingTop() + this.getAdjustedHeight() / 2);
                    if (delta == 0) {
                        delta = velocityY > 0 ? 1 : -1;
                    }

                    int finalPosition = Math.max(0, Math.min(this.getAdapter().getItemCount() -
                            1, currentPosition + delta));
                    this.smoothScrollToPosition(finalPosition);
                    return true;
                }
            } else {
                return super.fling(velocityX, velocityY);
            }
        }
    }

    public void smoothScrollToPosition(int position, android.support.v7.widget.RecyclerView
            .SmoothScroller smoothScroller) {
        WearableListView.LayoutManager layoutManager = (WearableListView.LayoutManager) this
                .getLayoutManager();
        layoutManager.setCustomSmoothScroller(smoothScroller);
        this.smoothScrollToPosition(position);
        layoutManager.clearCustomSmoothScroller();
    }

    public WearableListView.ViewHolder getChildViewHolder(View child) {
        return (WearableListView.ViewHolder) super.getChildViewHolder(child);
    }

    public void setClickListener(WearableListView.ClickListener clickListener) {
        this.mClickListener = clickListener;
    }

    public void setOverScrollListener(WearableListView.OnOverScrollListener listener) {
        this.mOverScrollListener = listener;
    }

    private int findCenterViewIndex() {
        int count = this.getChildCount();
        int index = -1;
        int closest = 2147483647;
        int centerY = getCenterYPos(this);

        for (int i = 0; i < count; ++i) {
            View child = this.getChildAt(i);
            int childCenterY = this.getTop() + getCenterYPos(child);
            int distance = Math.abs(centerY - childCenterY);
            if (distance < closest) {
                closest = distance;
                index = i;
            }
        }

        if (index == -1) {
            throw new IllegalStateException("Can\'t find central view.");
        } else {
            return index;
        }
    }

    private static int getCenterYPos(View v) {
        return v.getTop() + v.getPaddingTop() + getAdjustedHeight(v) / 2;
    }

    private void handleTouchUp(MotionEvent event, int scrollState) {
        if (this.mCanClick && event != null && this.checkForTap(event)) {
            Handler handler = this.getHandler();
            if (handler != null) {
                handler.postDelayed(this.mReleasedRunnable, (long) ViewConfiguration
                        .getTapTimeout());
            }

        } else if (scrollState == 0) {
            if (this.isOverScrolling()) {
                this.mOverScrollListener.onOverScroll();
            } else {
                this.animateToCenter();
            }

        }
    }

    private boolean isOverScrolling() {
        return this.getChildCount() > 0 && this.mStartFirstTop <= (float) this.getCentralViewTop
                () && this.getChildAt(0).getTop() >= this.getTopViewMaxTop() && this
                .mOverScrollListener != null;
    }

    private int getTopViewMaxTop() {
        return this.getHeight() / 2;
    }

    private int getItemHeight() {
        return this.getAdjustedHeight() / 3 + 1;
    }

    public int getCentralViewTop() {
        return this.getPaddingTop() + this.getItemHeight();
    }

    public void animateToCenter() {
        if (this.getChildCount() != 0) {
            int index = this.findCenterViewIndex();
            View child = this.getChildAt(index);
            int scrollToMiddle = this.getCentralViewTop() - child.getTop();
            this.startScrollAnimation(scrollToMiddle, 150L, 0L, new SimpleAnimatorListener() {
                public void onAnimationEnd(Animator animator) {
                    if (!this.wasCanceled()) {
                        WearableListView.this.mCanClick = true;
                    }

                }
            });
        }
    }

    public void animateToInitialPosition(final Runnable endAction) {
        View child = this.getChildAt(0);
        int scrollToMiddle = this.getCentralViewTop() + this.mInitialOffset - child.getTop();
        this.startScrollAnimation(scrollToMiddle, 150L, 0L, new SimpleAnimatorListener() {
            public void onAnimationEnd(Animator animator) {
                if (endAction != null) {
                    endAction.run();
                }

            }
        });
    }

    private void handleTouchDown(MotionEvent event) {
        if (this.mCanClick) {
            this.mTapPositionX = (int) event.getX();
            this.mTapPositionY = (int) event.getY();
            float rawY = event.getRawY();
            this.computeTapRegions(this.mTapRegions);
            if (rawY > this.mTapRegions[0] && rawY < this.mTapRegions[1]) {
                View view = this.getChildAt(this.findCenterViewIndex());
                if (view instanceof WearableListView.OnCenterProximityListener) {
                    Handler handler = this.getHandler();
                    if (handler != null) {
                        handler.removeCallbacks(this.mReleasedRunnable);
                        handler.postDelayed(this.mPressedRunnable, (long) ViewConfiguration
                                .getTapTimeout());
                    }
                }
            }
        }

    }

    private void setScrollVertically(int scroll) {
        this.scrollBy(0, scroll - this.mLastScrollChange);
        this.mLastScrollChange = scroll;
    }

    private int getAdjustedHeight() {
        return getAdjustedHeight(this);
    }

    private static int getAdjustedHeight(View v) {
        return v.getHeight() - v.getPaddingBottom() - v.getPaddingTop();
    }

    private void computeTapRegions(float[] tapRegions) {
        this.mLocation[0] = this.mLocation[1] = 0;
        this.getLocationOnScreen(this.mLocation);
        int mScreenTop = this.mLocation[1];
        int height = this.getHeight();
        tapRegions[0] = (float) mScreenTop + (float) height * 0.33F;
        tapRegions[1] = (float) mScreenTop + (float) height * 0.66999996F;
    }

    public boolean getMaximizeSingleItem() {
        return this.mMaximizeSingleItem;
    }

    public void setMaximizeSingleItem(boolean maximizeSingleItem) {
        this.mMaximizeSingleItem = maximizeSingleItem;
    }

    private void notifyChildrenAboutProximity(boolean animate) {
        //onAllItemScroll(animate);

        WearableListView.LayoutManager layoutManager = (WearableListView.LayoutManager) this
                .getLayoutManager();
        int count = layoutManager.getChildCount();
        if (count != 0) {
            int index = layoutManager.findCenterViewIndex();

            int position;
            for (position = 0; position < count; ++position) {
                View view = layoutManager.getChildAt(position);
                WearableListView.ViewHolder listener = this.getChildViewHolder(view);
                listener.onCenterProximity(position == index, animate);

            }

            position = this.getChildViewHolder(this.getChildAt(index)).getPosition();
            if (position != this.mPreviousCentral) {
                Iterator var8 = this.mOnScrollListeners.iterator();

                while (var8.hasNext()) {
                    WearableListView.OnScrollListener var9 = (WearableListView.OnScrollListener)
                            var8.next();
                    var9.onCentralPositionChanged(position);
                }

                var8 = this.mOnCentralPositionChangedListeners.iterator();

                while (var8.hasNext()) {
                    WearableListView.OnCentralPositionChangedListener var10 = (WearableListView
                            .OnCentralPositionChangedListener) var8.next();
                    var10.onCentralPositionChanged(position);
                }

                this.mPreviousCentral = position;
            }

        }
    }

    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        this.mObserver.setListView(this);
    }

    protected void onDetachedFromWindow() {
        this.mObserver.setListView((WearableListView) null);
        super.onDetachedFromWindow();
    }

    private static class OnChangeObserver extends AdapterDataObserver implements
            OnLayoutChangeListener {
        private WeakReference<WearableListView> mListView;
        private android.support.v7.widget.RecyclerView.Adapter mAdapter;
        private boolean mIsObservingAdapter;
        private boolean mIsListeningToLayoutChange;

        private OnChangeObserver() {
        }

        public void setListView(WearableListView listView) {
            this.stopOnLayoutChangeListening();
            this.mListView = new WeakReference(listView);
        }

        public void setAdapter(android.support.v7.widget.RecyclerView.Adapter adapter) {
            this.stopDataObserving();
            this.mAdapter = adapter;
            this.startDataObserving();
        }

        private void startDataObserving() {
            if (this.mAdapter != null) {
                this.mAdapter.registerAdapterDataObserver(this);
                this.mIsObservingAdapter = true;
            }

        }

        private void stopDataObserving() {
            this.stopOnLayoutChangeListening();
            if (this.mIsObservingAdapter) {
                this.mAdapter.unregisterAdapterDataObserver(this);
                this.mIsObservingAdapter = false;
            }

        }

        private void startOnLayoutChangeListening() {
            WearableListView listView = this.mListView == null ? null : (WearableListView) this
                    .mListView.get();
            if (!this.mIsListeningToLayoutChange && listView != null) {
                listView.addOnLayoutChangeListener(this);
                this.mIsListeningToLayoutChange = true;
            }

        }

        private void stopOnLayoutChangeListening() {
            if (this.mIsListeningToLayoutChange) {
                WearableListView listView = this.mListView == null ? null : (WearableListView)
                        this.mListView.get();
                if (listView != null) {
                    listView.removeOnLayoutChangeListener(this);
                }

                this.mIsListeningToLayoutChange = false;
            }

        }

        public void onChanged() {
            this.startOnLayoutChangeListening();
        }

        public void onLayoutChange(View view, int i, int i1, int i2, int i3, int i4, int i5, int
                i6, int i7) {
            WearableListView listView = (WearableListView) this.mListView.get();
            if (listView != null) {
                this.stopOnLayoutChangeListening();
                if (listView.getChildCount() > 0) {
                    listView.animateToCenter();
                }

            }
        }
    }

    private class SetScrollVerticallyProperty extends Property<WearableListView, Integer> {
        public SetScrollVerticallyProperty() {
            super(Integer.class, "scrollVertically");
        }

        public Integer get(WearableListView wearableListView) {
            return Integer.valueOf(wearableListView.mLastScrollChange);
        }

        public void set(WearableListView wearableListView, Integer value) {
            wearableListView.setScrollVertically(value.intValue());
        }
    }

    public static class ViewHolder extends android.support.v7.widget.RecyclerView.ViewHolder {
        public ViewHolder(View itemView) {
            super(itemView);
        }

        protected void onCenterProximity(boolean isCentralItem, boolean animate) {
            if (this.itemView instanceof WearableListView.OnCenterProximityListener) {
                WearableListView.OnCenterProximityListener item = (WearableListView
                        .OnCenterProximityListener) this.itemView;
                if (isCentralItem) {
                    item.onCenterPosition(animate);
                } else {
                    item.onNonCenterPosition(animate);
                }

            }
        }

        protected void onItemScroll(boolean isScroll) {
            WearableListView.onItemScroll item = (WearableListView
                    .onItemScroll) this.itemView;
            if (isScroll) {
                item.onScrollStart();
            } else {
                item.onScrollStoped();
            }
        }

        protected void onItemTouch(boolean isDown) {
            WearableListView.onItemTouch item = (WearableListView
                    .onItemTouch) this.itemView;
            if (isDown) {
                item.onTouchDown();
            } else {
                item.onTouchUp();
            }
        }

    }


    private static class SmoothScroller extends LinearSmoothScroller {
        private static final float MILLISECONDS_PER_INCH = 100.0F;
        private final WearableListView.LayoutManager mLayoutManager;

        public SmoothScroller(Context context, WearableListView.LayoutManager manager) {
            super(context);
            this.mLayoutManager = manager;
        }

        protected void onStart() {
            super.onStart();
        }

        protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
            return 100.0F / (float) displayMetrics.densityDpi;
        }

        public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int
                snapPreference) {
            return (boxStart + boxEnd) / 2 - (viewStart + viewEnd) / 2;
        }

        public PointF computeScrollVectorForPosition(int targetPosition) {
            return targetPosition < this.mLayoutManager.getFirstPosition() ?
                    new PointF(0.0F, -1.0F) : new PointF(0.0F, 1.0F);
        }
    }

    public abstract static class Adapter extends android.support.v7.widget.RecyclerView
            .Adapter<WearableListView.ViewHolder> {
        public Adapter() {
        }
    }

    public interface OnCentralPositionChangedListener {
        void onCentralPositionChanged(int var1);
    }

    public interface OnScrollListener {
        void onScroll(int var1);

        /**
         * @deprecated
         */
        @Deprecated
        void onAbsoluteScrollChange(int var1);

        void onScrollStateChanged(int var1);

        void onCentralPositionChanged(int var1);
    }

    public interface OnOverScrollListener {
        void onOverScroll();
    }

    public interface ClickListener {
        void onClick(WearableListView.ViewHolder var1);

        void onTopEmptyRegionClick();
    }

    public interface OnCenterProximityListener {
        void onCenterPosition(boolean var1);

        void onNonCenterPosition(boolean var1);
    }

    public interface onItemScroll {
        void onScrollStart();

        void onScrollStoped();
    }

    public interface onItemTouch {
        void onTouchDown();

        void onTouchUp();
    }

    private class LayoutManager extends android.support.v7.widget.RecyclerView.LayoutManager {
        private int mFirstPosition;
        private boolean mPushFirstHigher;
        private int mAbsoluteScroll;
        private boolean mUseOldViewTop;
        private boolean mWasZoomedIn;
        private android.support.v7.widget.RecyclerView.SmoothScroller mSmoothScroller;
        private android.support.v7.widget.RecyclerView.SmoothScroller mDefaultSmoothScroller;

        private LayoutManager() {
            this.mUseOldViewTop = true;
            this.mWasZoomedIn = false;
        }

        private int findCenterViewIndex() {
            int count = this.getChildCount();
            int index = -1;
            int closest = 2147483647;
            int centerY = WearableListView.getCenterYPos(WearableListView.this);

            for (int i = 0; i < count; ++i) {
                View child = WearableListView.this.getLayoutManager().getChildAt(i);
                int childCenterY = WearableListView.this.getTop() + WearableListView
                        .getCenterYPos(child);
                int distance = Math.abs(centerY - childCenterY);
                if (distance < closest) {
                    closest = distance;
                    index = i;
                }
            }

            if (index == -1) {
                throw new IllegalStateException("Can\'t find central view.");
            } else {
                return index;
            }
        }

        public void onLayoutChildren(Recycler recycler, State state) {
            int parentBottom = this.getHeight() - this.getPaddingBottom();
            int oldTop = WearableListView.this.getCentralViewTop() + WearableListView.this
                    .mInitialOffset;
            if (this.mUseOldViewTop && this.getChildCount() > 0) {
                int child = this.findCenterViewIndex();
                int position = this.getPosition(this.getChildAt(child));
                int count;
                if (position == -1) {
                    count = 0;

                    for (int N = this.getChildCount(); child + count < N || child - count >= 0;
                         ++count) {
                        View child1 = this.getChildAt(child + count);
                        if (child1 != null) {
                            position = this.getPosition(child1);
                            if (position != -1) {
                                child += count;
                                break;
                            }
                        }

                        child1 = this.getChildAt(child - count);
                        if (child1 != null) {
                            position = this.getPosition(child1);
                            if (position != -1) {
                                child -= count;
                                break;
                            }
                        }
                    }
                }

                if (position == -1) {
                    oldTop = this.getChildAt(0).getTop();

                    for (count = state.getItemCount(); this.mFirstPosition >= count && this
                            .mFirstPosition > 0; --this.mFirstPosition) {
                        ;
                    }
                } else {
                    if (!this.mWasZoomedIn) {
                        oldTop = this.getChildAt(child).getTop();
                    }

                    while (oldTop > this.getPaddingTop() && position > 0) {
                        --position;
                        oldTop -= WearableListView.this.getItemHeight();
                    }

                    if (position == 0 && oldTop > WearableListView.this.getCentralViewTop()) {
                        oldTop = WearableListView.this.getCentralViewTop();
                    }

                    this.mFirstPosition = position;
                }
            } else if (this.mPushFirstHigher) {
                oldTop = WearableListView.this.getCentralViewTop() - WearableListView.this
                        .getItemHeight();
            }

            this.performLayoutChildren(recycler, state, parentBottom, oldTop);
            if (this.getChildCount() == 0) {
                this.setAbsoluteScroll(0);
            } else {
                View var10 = this.getChildAt(this.findCenterViewIndex());
                this.setAbsoluteScroll(var10.getTop() - WearableListView.this.getCentralViewTop()
                        + this.getPosition(var10) * WearableListView.this.getItemHeight());
            }

            this.mUseOldViewTop = true;
            this.mPushFirstHigher = false;
        }

        private void performLayoutChildren(Recycler recycler, State state, int parentBottom, int
                top) {
            this.detachAndScrapAttachedViews(recycler);
            if (WearableListView.this.mMaximizeSingleItem && state.getItemCount() == 1) {
                this.performLayoutOneChild(recycler, parentBottom);
                this.mWasZoomedIn = true;
            } else {
                this.performLayoutMultipleChildren(recycler, state, parentBottom, top);
                this.mWasZoomedIn = false;
            }

            if (this.getChildCount() > 0) {
                WearableListView.this.post(WearableListView.this.mNotifyChildrenPostLayoutRunnable);
            }

        }

        private void performLayoutOneChild(Recycler recycler, int parentBottom) {
            int right = this.getWidth() - this.getPaddingRight();
            View v = recycler.getViewForPosition(this.getFirstPosition());
            this.addView(v, 0);
            this.measureZoomView(v);
            v.layout(this.getPaddingLeft(), this.getPaddingTop(), right, parentBottom);
        }

        private void performLayoutMultipleChildren(Recycler recycler, State state, int
                parentBottom, int top) {
            int left = this.getPaddingLeft();
            int right = this.getWidth() - this.getPaddingRight();
            int count = state.getItemCount();

            int bottom;
            for (int i = 0; this.getFirstPosition() + i < count && top < parentBottom; top =
                    bottom) {
                View v = recycler.getViewForPosition(this.getFirstPosition() + i);
                this.addView(v, i);
                this.measureThirdView(v);
                bottom = top + WearableListView.this.getItemHeight();
                v.layout(left, top, right, bottom);
                ++i;
            }

        }

        private void setAbsoluteScroll(int absoluteScroll) {
            this.mAbsoluteScroll = absoluteScroll;
            Iterator var2 = WearableListView.this.mOnScrollListeners.iterator();

            while (var2.hasNext()) {
                WearableListView.OnScrollListener listener = (WearableListView.OnScrollListener)
                        var2.next();
                listener.onAbsoluteScrollChange(this.mAbsoluteScroll);
            }

        }

        private void measureView(View v, int height) {
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            int widthSpec = getChildMeasureSpec(this.getWidth(), this.getPaddingLeft() + this
                    .getPaddingRight() + lp.leftMargin + lp.rightMargin, lp.width, this
                    .canScrollHorizontally());
            int heightSpec = getChildMeasureSpec(this.getHeight(), this.getPaddingTop() + this
                    .getPaddingBottom() + lp.topMargin + lp.bottomMargin, height, this
                    .canScrollVertically());
            v.measure(widthSpec, heightSpec);
        }

        private void measureThirdView(View v) {
            this.measureView(v, (int) (1.0F + (float) this.getHeight() / 3.0F));
        }

        private void measureZoomView(View v) {
            this.measureView(v, this.getHeight());
        }

        public LayoutParams generateDefaultLayoutParams() {
            return new LayoutParams(-1, -2);
        }

        public boolean canScrollVertically() {
            return this.getItemCount() != 1 || !this.mWasZoomedIn;
        }

        public int scrollVerticallyBy(int dy, Recycler recycler, State state) {
            if (this.getChildCount() == 0) {
                return 0;
            } else {
                int scrolled = 0;
                int left = this.getPaddingLeft();
                int right = this.getWidth() - this.getPaddingRight();
                int scrollBy;
                int top;
                if (dy < 0) {
                    while (scrolled > dy) {
                        View parentHeight1 = this.getChildAt(0);
                        int bottomView1;
                        if (this.getFirstPosition() <= 0) {
                            this.mPushFirstHigher = false;
                            bottomView1 = WearableListView.this.mOverScrollListener != null ?
                                    this.getHeight() : WearableListView.this.getTopViewMaxTop();
                            scrollBy = Math.min(-dy + scrolled, bottomView1 - parentHeight1
                                    .getTop());
                            scrolled -= scrollBy;
                            this.offsetChildrenVertical(scrollBy);
                            break;
                        }

                        bottomView1 = Math.max(-parentHeight1.getTop(), 0);
                        scrollBy = Math.min(scrolled - dy, bottomView1);
                        scrolled -= scrollBy;
                        this.offsetChildrenVertical(scrollBy);
                        if (this.getFirstPosition() <= 0 || scrolled <= dy) {
                            break;
                        }

                        --this.mFirstPosition;
                        View scrollBy2 = recycler.getViewForPosition(this.getFirstPosition());
                        this.addView(scrollBy2, 0);
                        this.measureThirdView(scrollBy2);
                        int v1 = parentHeight1.getTop();
                        top = v1 - WearableListView.this.getItemHeight();
                        scrollBy2.layout(left, top, right, v1);
                    }
                } else if (dy > 0) {
                    int parentHeight = this.getHeight();

                    while (scrolled < dy) {
                        View bottomView = this.getChildAt(this.getChildCount() - 1);
                        if (state.getItemCount() <= this.mFirstPosition + this.getChildCount()) {
                            scrollBy = Math.max(-dy + scrolled, this.getHeight() / 2 - bottomView
                                    .getBottom());
                            scrolled -= scrollBy;
                            this.offsetChildrenVertical(scrollBy);
                            break;
                        }

                        scrollBy = Math.max(bottomView.getBottom() - parentHeight, 0);
                        int scrollBy1 = -Math.min(dy - scrolled, scrollBy);
                        scrolled -= scrollBy1;
                        this.offsetChildrenVertical(scrollBy1);
                        if (scrolled >= dy) {
                            break;
                        }

                        View v = recycler.getViewForPosition(this.mFirstPosition + this
                                .getChildCount());
                        top = this.getChildAt(this.getChildCount() - 1).getBottom();
                        this.addView(v);
                        this.measureThirdView(v);
                        int bottom = top + WearableListView.this.getItemHeight();
                        v.layout(left, top, right, bottom);
                    }
                }

                this.recycleViewsOutOfBounds(recycler);
                this.setAbsoluteScroll(this.mAbsoluteScroll + scrolled);
                return scrolled;
            }
        }

        public void scrollToPosition(int position) {
            this.mUseOldViewTop = false;
            if (position > 0) {
                this.mFirstPosition = position - 1;
                this.mPushFirstHigher = true;
            } else {
                this.mFirstPosition = position;
                this.mPushFirstHigher = false;
            }

            this.requestLayout();
        }

        public void setCustomSmoothScroller(android.support.v7.widget.RecyclerView.SmoothScroller
                                                    smoothScroller) {
            this.mSmoothScroller = smoothScroller;
        }

        public void clearCustomSmoothScroller() {
            this.mSmoothScroller = null;
        }

        public android.support.v7.widget.RecyclerView.SmoothScroller getDefaultSmoothScroller
                (RecyclerView recyclerView) {
            if (this.mDefaultSmoothScroller == null) {
                this.mDefaultSmoothScroller = new WearableListView.SmoothScroller(recyclerView
                        .getContext(), this);
            }

            return this.mDefaultSmoothScroller;
        }

        public void smoothScrollToPosition(RecyclerView recyclerView, State state, int position) {
            android.support.v7.widget.RecyclerView.SmoothScroller scroller = this.mSmoothScroller;
            if (scroller == null) {
                scroller = this.getDefaultSmoothScroller(recyclerView);
            }

            scroller.setTargetPosition(position);
            this.startSmoothScroll(scroller);
        }

        private void recycleViewsOutOfBounds(Recycler recycler) {
            int childCount = this.getChildCount();
            int parentWidth = this.getWidth();
            int parentHeight = this.getHeight();
            boolean foundFirst = false;
            int first = 0;
            int last = 0;

            int i;
            for (i = 0; i < childCount; ++i) {
                View v = this.getChildAt(i);
                if (v.hasFocus() || v.getRight() >= 0 && v.getLeft() <= parentWidth && v
                        .getBottom() >= 0 && v.getTop() <= parentHeight) {
                    if (!foundFirst) {
                        first = i;
                        foundFirst = true;
                    }

                    last = i;
                }
            }

            for (i = childCount - 1; i > last; --i) {
                this.removeAndRecycleViewAt(i, recycler);
            }

            for (i = first - 1; i >= 0; --i) {
                this.removeAndRecycleViewAt(i, recycler);
            }

            if (this.getChildCount() == 0) {
                this.mFirstPosition = 0;
            } else if (first > 0) {
                this.mPushFirstHigher = true;
                this.mFirstPosition += first;
            }

        }

        public int getFirstPosition() {
            return this.mFirstPosition;
        }

        public void onAdapterChanged(android.support.v7.widget.RecyclerView.Adapter oldAdapter,
                                     android.support.v7.widget.RecyclerView.Adapter newAdapter) {
            this.removeAllViews();
        }
    }
}
