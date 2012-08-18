package com.joebateson.hallApp;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class MyListAdapter extends BaseAdapter {
    
    private Activity activity;
    private ArrayList<String> values;
    private ArrayList<String> details;
    private static LayoutInflater inflater = null;

    public MyListAdapter(Activity a, ArrayList<String> values, ArrayList<String> details) {
        this.activity = a;
        this.values = values;
        this.details = details;
        MyListAdapter.inflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);      
    }
    
    @Override
    public int getCount() {
        return values.size();
    }

    @Override
    public Object getItem(int position) {
        SimpleDateFormat format = DisplayHallInfoActivity.formatPretty;       
        try {
            return format.parse(values.get(position));
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
    
    public static class ViewHolder{
        public TextView text1;
        public TextView text2;
        public ImageView image;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View vi = convertView;
        ViewHolder holder;
        
        if (convertView == null) {
            vi = inflater.inflate(R.layout.grid_list_layout, null);
            
            holder = new ViewHolder();
            holder.text1 = (TextView) vi.findViewById(R.id.item1);
            holder.text2 = (TextView) vi.findViewById(R.id.item2);
            holder.image = (ImageView)vi.findViewById(R.id.icon);
   
            vi.setTag(holder);
        } else {
            holder = (ViewHolder) vi.getTag();
        }
   
        holder.text1.setText(this.values.get(position));
        holder.text2.setText(this.details.get(position));
        
        if (this.details.get(position).equals("No Hall")) {
            holder.image.setImageResource(R.drawable.x_grey);
        } else if (this.details.get(position).indexOf("Formal Hall") != -1) {
            holder.image.setImageResource(R.drawable.check_blue);
        } else {
            holder.image.setImageResource(R.drawable.check_cyan);
        }
   
        return vi;
    }

}
