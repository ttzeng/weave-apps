package com.intel.ttzeng.myweaveapp;

import android.content.Intent;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.Pair;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.apps.weave.apis.data.ModelManifest;
import com.google.android.apps.weave.apis.data.WeaveDevice;

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {
    private final static String TAG = DeviceListAdapter.class.getSimpleName();

    private final ArrayMap<String, Pair<WeaveDevice, ModelManifest>> mDataSet;

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView name;
        public final TextView description;
        public final TextView deviceType;
        public final ImageView deviceImage;

        public ViewHolder(final View parentView, final TextView name,
                          final TextView description, final TextView deviceType,
                          final ImageView deviceImage) {
            super(parentView);
            this.name = name;
            this.description = description;
            this.deviceType = deviceType;
            this.deviceImage = deviceImage;
            parentView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = getAdapterPosition();
                    WeaveDevice device = mDataSet.valueAt(position).first;

                    Intent intent = new Intent(v.getContext(), DeviceCtrlActivity.class);
                    intent.putExtra(DeviceCtrlActivity.EXTRA_KEY_WEAVE_DEVICE, device);
                    v.getContext().startActivity(intent);
                }
            });
        }
    }

    public DeviceListAdapter() {
        mDataSet = new ArrayMap<>();
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Called when RecyclerView needs a new RecyclerView.ViewHolder of the given type to represent an item
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.viewholder_device, parent, false);
        return new ViewHolder(v, (TextView) v.findViewById(R.id.device_title),
                                 (TextView) v.findViewById(R.id.device_description),
                                 (TextView) v.findViewById(R.id.device_device_type),
                                 (ImageView) v.findViewById(R.id.device_picture));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // Called by RecyclerView to display the data at the specified position
        Pair<WeaveDevice, ModelManifest> data= mDataSet.valueAt(position);
        WeaveDevice device  = data.first;
        ModelManifest model = data.second;
        holder.name.setText(device.getName());
        holder.description.setText(device.getDescription());
        if (model != null) {
            holder.deviceType.setText(model.getModelName());
        } else {
            holder.deviceType.setText(R.string.null_manifest);
        }
        if (device.getDiscoveryTransport().hasCloud()) {
            holder.deviceImage.setImageResource(R.drawable.ic_cast_on_light);
        } else {
            holder.deviceImage.setImageResource(R.drawable.ic_setting_light);
        }
    }

    public void add(WeaveDevice device, ModelManifest manifest) {
        Log.d(TAG, "Adding device " + device.getId());
        mDataSet.put(device.getId(), new Pair<>(device, manifest));
    }

    public void remove(WeaveDevice device) {
        mDataSet.remove(device.getId());
    }
}
