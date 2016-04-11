package com.intel.ttzeng.myweaveapp;

import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.apps.weave.apis.data.Command;
import com.google.android.apps.weave.apis.data.CommandResult;
import com.google.android.apps.weave.apis.data.DeviceState;
import com.google.android.apps.weave.apis.data.WeaveApiClient;
import com.google.android.apps.weave.apis.data.WeaveDevice;
import com.google.android.apps.weave.apis.data.responses.Response;
import com.google.android.apps.weave.framework.apis.Weave;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DeviceAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = DeviceAdapter.class.getSimpleName();
    public enum CardTypes {
        OnOffView,
        MediaPlayerView,
        VolumeView,
    }
    private Context mContext;
    private ArrayList<CardTypes> mDataSet;
    private WeaveApiClient mApiClient;
    private WeaveDevice mDevice;
    private enum MediaState {
        Idle,
        Play,
        Pause,
    }
    private MediaState mState;

    class OnOffViewHolder extends RecyclerView.ViewHolder {
        private Switch mOnOffToggle;
        OnOffViewHolder(final View parentView) {
            super(parentView);
            mOnOffToggle = (Switch) parentView.findViewById(R.id.switch_onoff);
            mOnOffToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    SetOnOffState(mOnOffToggle);
                };
            });
        }
    }

    class MediaPlayerViewHolder extends RecyclerView.ViewHolder {
        private View mContainerView;
        MediaPlayerViewHolder(final View parentView) {
            super(parentView);
            mState = MediaState.Idle;
            mContainerView = parentView;
            mContainerView.findViewById(R.id.btn_play_pause).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SetMediaPlayer((mState == MediaState.Play) ? MediaState.Pause : MediaState.Play);
                }
            });
            mContainerView.findViewById(R.id.btn_stop).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    SetMediaPlayer(MediaState.Idle);
                }
            });
        }
    }

    class VolumeViewHolder extends RecyclerView.ViewHolder {
        private SeekBar mVolumeCtrl;
        VolumeViewHolder(final View parentView) {
            super(parentView);
            mVolumeCtrl = (SeekBar) parentView.findViewById(R.id.volumeLevel);
            mVolumeCtrl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    SetVolume(progress);
                }
            });
        }
    }

    DeviceAdapter(Context context, WeaveDevice device) {
        mContext = context;
        mDataSet = new ArrayList<>();
        mApiClient = new WeaveApiClient(context);
        mDevice = device;
    }

    @Override
    public int getItemViewType(int position) {
        int type = mDataSet.get(position).ordinal();
        return type;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;
        RecyclerView.ViewHolder card = null;
        if (viewType == CardTypes.OnOffView.ordinal()) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_onoff, parent, false);
            card = new OnOffViewHolder(v);
            updateOnOffState((OnOffViewHolder) card);
        } else if (viewType == CardTypes.MediaPlayerView.ordinal()) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_mediaplayer, parent, false);
            card = new MediaPlayerViewHolder(v);
            updateMediaPlayerState((MediaPlayerViewHolder) card);
        } else if (viewType == CardTypes.VolumeView.ordinal()) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_volume, parent, false);
            card = new VolumeViewHolder(v);
            updateVolumeState((VolumeViewHolder) card);
        }
        return card;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        Log.d(TAG, "onBindViewHolder(" + position + ")");
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    public void add(CardTypes type) {
        mDataSet.add(type);
        notifyDataSetChanged();
    }

    public void clear() {
        mDataSet.clear();
        notifyDataSetChanged();
    }

    private void SetOnOffState(final Switch sw) {
        final boolean on = sw.isChecked();
        Log.d(TAG, "SetOnOffState(" + on + ")");
        new AsyncTask<Void, Void, Response<CommandResult>>() {
            @Override
            protected Response<CommandResult> doInBackground(Void... voids) {
                HashMap<String, Object> commandParams = new HashMap<>();
                Resources res = mContext.getResources();
                commandParams.put("state", on ? res.getString(R.string.onOff_st_on) : res.getString(R.string.onOff_st_off));
                Command command = new Command()
                        .setName(res.getString(R.string.onOff_cmd_setConfig))
                        .setParameters(commandParams);
                return Weave.COMMAND_API.execute(mApiClient, mDevice.getId(), command);
            }

            @Override
            protected void onPostExecute(Response<CommandResult> result) {
                super.onPostExecute(result);
                if (result != null) {
                    if (!result.isSuccess() || result.getError() != null) {
                        Log.e(TAG, "Failure setting device state: " + result.getError());
                        sw.setChecked(!on);
                    } else {
                        Log.i(TAG, "Success set device state");
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void SetMediaPlayer(final MediaState state) {
        new AsyncTask<Void, Void, Response<CommandResult>>() {
            @Override
            protected Response<CommandResult> doInBackground(Void... voids) {
                HashMap<String, Object> commandParams = new HashMap<>();
                Resources res = mContext.getResources();
                int cmd_str_id = R.string._mediaplayer_cmd_stop;
                switch (state) {
                    case Play:
                        cmd_str_id = R.string._mediaplayer_cmd_play;
                        break;
                    case Pause:
                        cmd_str_id = R.string._mediaplayer_cmd_pause;
                }
                Command command = new Command().setName(res.getString(cmd_str_id));
                return Weave.COMMAND_API.execute(mApiClient, mDevice.getId(), command);
            }

            @Override
            protected void onPostExecute(Response<CommandResult> result) {
                super.onPostExecute(result);
                if (result != null) {
                    if (!result.isSuccess() || result.getError() != null) {
                        Log.e(TAG, "Failure setting media player state: " + result.getError());
                    } else {
                        Log.i(TAG, "Success set media player state");
                        mState = state;
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void SetVolume(final int volume) {
        Log.d(TAG, "SetVolume(" + volume + ")");
        new AsyncTask<Void, Void, Response<CommandResult>>() {
            @Override
            protected Response<CommandResult> doInBackground(Void... voids) {
                HashMap<String, Object> commandParams = new HashMap<>();
                Resources res = mContext.getResources();
                commandParams.put("volume", volume);
                Command command = new Command()
                        .setName(res.getString(R.string.volume_cmd_setConfig))
                        .setParameters(commandParams);
                return Weave.COMMAND_API.execute(mApiClient, mDevice.getId(), command);
            }

            @Override
            protected void onPostExecute(Response<CommandResult> result) {
                super.onPostExecute(result);
                if (result != null) {
                    if (!result.isSuccess() || result.getError() != null) {
                        Log.e(TAG, "Failure setting audio volume: " + result.getError());
                    } else {
                        Log.i(TAG, "Success set audio volume");
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void updateOnOffState(final OnOffViewHolder holder) {
        new AsyncTask<Void, Void, Response<DeviceState>>() {
            @Override
            protected Response<DeviceState> doInBackground(Void... voids) {
                return Weave.COMMAND_API.getState(mApiClient, mDevice.getId());
            }

            @Override
            protected void onPostExecute(Response<DeviceState> result) {
                super.onPostExecute(result);
                if (result != null) {
                    if (!result.isSuccess() || result.getError() != null) {
                        Log.e(TAG, "Failure get device state. " + result.getError());
                    } else {
                        Map<String, Object> state = (Map<String, Object>)result.getSuccess().getStateValue("onOff");
                        if (state != null) {
                            String st =  (String) state.get("state");
                            boolean on = st.equals(mContext.getResources().getString(R.string.onOff_st_on));
                            Log.i(TAG, "Device state = " + on);
                            if (holder.mOnOffToggle.isChecked() != on)
                                holder.mOnOffToggle.setChecked(on);
                        }
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void updateMediaPlayerState(final MediaPlayerViewHolder holder) {
        new AsyncTask<Void, Void, Response<DeviceState>>() {
            @Override
            protected Response<DeviceState> doInBackground(Void... voids) {
                return Weave.COMMAND_API.getState(mApiClient, mDevice.getId());
            }

            @Override
            protected void onPostExecute(Response<DeviceState> result) {
                super.onPostExecute(result);
                if (result != null) {
                    if (!result.isSuccess() || result.getError() != null) {
                        Log.e(TAG, "Failure get device state. " + result.getError());
                    } else {
                        Map<String, Object> state = (Map<String, Object>)result.getSuccess().getStateValue("_mediaplayer");
                        if (state != null) {
                            String status = (String) state.get("status");
                            Log.i(TAG, "status = " + status);
                            if (status.equals("idle"))
                                mState = MediaState.Idle;
                            else if (status.equals("playing"))
                                mState = MediaState.Play;
                            else
                                mState = MediaState.Pause;
                            String display =  (String) state.get("display");
                            Log.i(TAG, "display = " + display);
                            TextView v = (TextView) holder.mContainerView.findViewById(R.id.lbl_display);
                            v.setText(display);
                        }
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void updateVolumeState(final VolumeViewHolder holder) {
        new AsyncTask<Void, Void, Response<DeviceState>>() {
            @Override
            protected Response<DeviceState> doInBackground(Void... voids) {
                return Weave.COMMAND_API.getState(mApiClient, mDevice.getId());
            }

            @Override
            protected void onPostExecute(Response<DeviceState> result) {
                super.onPostExecute(result);
                if (result != null) {
                    if (!result.isSuccess() || result.getError() != null) {
                        Log.e(TAG, "Failure get device state. " + result.getError());
                    } else {
                        Map<String, Object> state = (Map<String, Object>)result.getSuccess().getStateValue("volume");
                        if (state != null) {
                            int volume =  Integer.parseInt(state.get("volume").toString());
                            Log.i(TAG, "Volume = " + volume);
                            holder.mVolumeCtrl.setProgress(volume);
                        }
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
