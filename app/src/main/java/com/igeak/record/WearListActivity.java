package com.igeak.record;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;

import com.igeak.record.WearableListView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Created by xuqiang on 16-7-1.
 */

public class WearListActivity extends Activity
        implements WearableListView.ClickListener, View.OnLayoutChangeListener {

    //String[] elements = { "List Item 1", "List Item 2" };
    File[] files;
    private RelativeLayout mImgRecordRl;
    WearableListView listView;
    MyListAdapter myListAdapter;
    private int mInitialHeaderHeight;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        files = getAllRecordFileNames();
        if (files.length == 0) {
            Toast.makeText(getApplicationContext(), getString(R.string.toast_file_not_exist),
                    Toast.LENGTH_LONG).show();
            finish();
        }
        setContentView(R.layout.my_list_activity);
        mImgRecordRl = (RelativeLayout) findViewById(R.id.item_img_rl);
        // Get the list component from the layout of the activity
        listView =
                (WearableListView) findViewById(R.id.wearable_list);
        myListAdapter = new MyListAdapter(this, files);
        // Assign an adapter to the list
        listView.setAdapter(myListAdapter);

        // Set a click listener
        listView.setClickListener(this);

        //listView.animateToCenter();
        listView.addOnCentralPositionChangedListener(new WearableListView
                .OnCentralPositionChangedListener() {
            @Override
            public void onCentralPositionChanged(int centerPosition) {
                /**
                 * 当选中第一个的时候，显示record图标
                 */
//                if (centerPosition > 0) {
//                    mImgRecordRl.setVisibility(View.GONE);
//                } else {
//                    mImgRecordRl.setVisibility(View.VISIBLE);
//                }

            }
        });
        listView.addOnScrollListener(new WearableListView.OnScrollListener() {
            @Override
            public void onScroll(int var1) {
                adjustHeaderTranslation();
            }

            @Override
            public void onAbsoluteScrollChange(int var1) {

            }

            @Override
            public void onScrollStateChanged(int var1) {

            }

            @Override
            public void onCentralPositionChanged(int var1) {

            }
        });
        mImgRecordRl.addOnLayoutChangeListener(this);
        listView.addOnLayoutChangeListener(this);


    }

    @Override
    public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int
            oldTop, int oldRight, int oldBottom) {
        if (v == mImgRecordRl) {
            mInitialHeaderHeight = bottom - top;
            mInitialHeaderHeight += ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).topMargin;

        } else if (v == listView) {
            adjustHeaderTranslation();
        }
    }


    private void adjustHeaderTranslation() {
        int translation = 0;
        if (listView.getChildCount() > 0) {
            translation = listView.getCentralViewTop() - listView.getChildAt(0).getTop();
        }
        float newTranslation = Math.min(Math.max(-mInitialHeaderHeight, -translation), 0);
        int position = listView.getChildPosition(this.listView.getChildAt(0));
        if (position != 0 && newTranslation >= 0) {
            return;
        }
        mImgRecordRl.setTranslationY(newTranslation);
    }

    private File[] getAllRecordFileNames() {
        File mfile = new File(MainActivity.FILE_PATH);
        File[] filelist = mfile.listFiles();
        Arrays.sort(filelist, new Comparator<File>() {
            @Override
            public int compare(File file, File t1) {
                return file.lastModified() < t1.lastModified() ? 1 : -1;
            }
        });
        return filelist;
    }

    // WearableListView click listener
    @Override
    public void onClick(WearableListView.ViewHolder v) {
        Integer tag = (Integer) v.itemView.getTag();
        // use this data to complete some action ...
        playMusic(tag);
    }

    @Override
    public void onTopEmptyRegionClick() {
        files = getAllRecordFileNames();
    }

    private void playMusic(int currentIndex) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("index", currentIndex);
        startActivityForResult(intent, Const.REQUESTCODE_PLAYER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((Const.REQUESTCODE_PLAYER == requestCode) && (Const.RESULTCODE_UPDATE == resultCode)) {
            files = getAllRecordFileNames();
            if (files.length == 0) {
//                Toast.makeText(getApplicationContext(), getString(R.string.toast_file_not_exist),
//                        Toast.LENGTH_LONG).show();
                finish();
            }
            myListAdapter.setFiles(files);
            myListAdapter.notifyDataSetChanged();
        }
    }


    private static final class MyListAdapter extends WearableListView.Adapter {
        private File[] files;
        private final Context mContext;
        private final LayoutInflater mInflater;


        // Provide a suitable constructor (depends on the kind of dataset)
        public MyListAdapter(Context context, File[] files) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
            this.files = files;
        }

        public void setFiles(File[] files) {
            this.files = files;
        }


        // Provide a reference to the type of views you're using
        public static final class ItemViewHolder extends WearableListView.ViewHolder {

            private RelativeLayout mImgRl;
            private ImageView mRecordIv;

            private RelativeLayout mDetailRl;
            private TextView mNameTv;
            private TextView mDateTv;
            private TextView mTimeTv;
            private TextView mDuationTv;
            private ImageView mDuationImg;

            private RelativeLayout mInfoupRl;
            private TextView mSmallNameupTv;

            private RelativeLayout mInfodownRl;
            private TextView mSmallNamedownTv;


            public ItemViewHolder(View itemView) {
                super(itemView);
                // find the text view within the custom item's layout
                mImgRl = (RelativeLayout) itemView.findViewById(R.id.item_img_rl);
                //mRecordIv = itemView.findViewById(R.id.)

                mDetailRl = (RelativeLayout) itemView.findViewById(R.id.item_detail_rl);
                mNameTv = (TextView) itemView.findViewById(R.id.record_name);
                mDateTv = (TextView) itemView.findViewById(R.id.record_date);
                mTimeTv = (TextView) itemView.findViewById(R.id.record_time);
                mDuationImg = (ImageView) itemView.findViewById(R.id.record_duration_img);
                mDuationTv = (TextView) itemView.findViewById(R.id.record_duration);

                mInfoupRl = (RelativeLayout) itemView.findViewById(R.id.item_info_rl_up);
                mSmallNameupTv = (TextView) itemView.findViewById(R.id.record_name_small_up);

                mInfodownRl = (RelativeLayout) itemView.findViewById(R.id.item_info_rl_down);
                mSmallNamedownTv = (TextView) itemView.findViewById(R.id.record_name_small_down);
            }
        }

        // Create new views for list items
        // (invoked by the WearableListView's layout manager)
        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                              int viewType) {
            // Inflate our custom layout for list items
            return new ItemViewHolder(mInflater.inflate(R.layout.list_item, null));
        }

        // Replace the contents of a list item
        // Instead of creating new views, the list tries to recycle existing ones
        // (invoked by the WearableListView's layout manager)
        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder,
                                     int position) {
            ItemViewHolder itemHolder = (ItemViewHolder) holder;

            if (position >= 0) {
                int currentIndex = position;

                File file = files[currentIndex];
                String dateString = file.getName().substring(3);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

                SimpleDateFormat tf = new SimpleDateFormat("mm:ss");


                try {
                    Date date = sdf.parse(dateString);
                    System.out.println(date);

                    // SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
                    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy/MM/dd");
                    String dateText = sdfDate.format(date);

                    SimpleDateFormat sdfName = new SimpleDateFormat("a HH:mm");
                    String timeText = sdfName.format(date);

                    MediaPlayer player = MediaPlayer.create(mContext, Uri.fromFile(file));
                    int time = player.getDuration();
                    if (player != null) {
                        player.release();
                        player = null;
                    }

                    String duration = tf.format(new Date(time));

                    String name = mContext.getString(R.string.record)
                            + " " + String.format("%02d", currentIndex + 1);
                    itemHolder.mNameTv.setText(name);
                    itemHolder.mSmallNameupTv.setText(name);
                    itemHolder.mSmallNamedownTv.setText(name);
                    itemHolder.mDateTv.setText(dateText);
                    itemHolder.mTimeTv.setText(timeText);
                    itemHolder.mDuationTv.setText(duration);

                    itemHolder.mImgRl.setVisibility(View.GONE);


                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                itemHolder.mImgRl.setVisibility(View.VISIBLE);
                itemHolder.mInfoupRl.setVisibility(View.GONE);
                itemHolder.mDetailRl.setVisibility(View.GONE);
            }

            holder.itemView.setTag(position);
        }

        // Return the size of your dataset
        // (invoked by the WearableListView's layout manager)
        @Override
        public int getItemCount() {
            return files.length;
        }
    }


}