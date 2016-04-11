package com.intel.ttzeng.myweaveapp;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.google.android.apps.weave.apis.data.DeviceState;
import com.google.android.apps.weave.apis.data.DiscoveryTransport;
import com.google.android.apps.weave.apis.data.WeaveApiClient;
import com.google.android.apps.weave.apis.data.WeaveDevice;
import com.google.android.apps.weave.apis.data.responses.Response;
import com.google.android.apps.weave.framework.apis.Weave;

import java.util.Map;

public class DeviceCtrlActivity extends AppCompatActivity {
    private static final String TAG = DeviceCtrlActivity.class.getSimpleName();
    public static final String EXTRA_KEY_WEAVE_DEVICE = BuildConfig.APPLICATION_ID + ".weave_device";

    private WeaveApiClient mApiClient;
    private WeaveDevice mDevice;
    private DeviceAdapter mCardsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.devicectrl);

        mApiClient = new WeaveApiClient(this);
        mDevice = getIntent().getParcelableExtra(EXTRA_KEY_WEAVE_DEVICE);
        if (mDevice == null) {
            throw new IllegalArgumentException("No Weave device set in the intent " + EXTRA_KEY_WEAVE_DEVICE);
        }

        RecyclerView recList = (RecyclerView) findViewById(R.id.card_main);
        recList.setHasFixedSize(true);
        LinearLayoutManager llm = new LinearLayoutManager(this);
        llm.setOrientation(LinearLayoutManager.VERTICAL);
        recList.setLayoutManager(llm);
        mCardsAdapter = new DeviceAdapter(this, mDevice);
        recList.setAdapter(mCardsAdapter);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        toolbar.setTitle(mDevice.getName());
        DiscoveryTransport discovery = mDevice.getDiscoveryTransport();
        if (discovery.hasCloud())
            toolbar.setSubtitle(discovery.getCloud().getConnectionStatus());
        else
            toolbar.setSubtitle(R.string.discovery_local);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        mCardsAdapter.clear();
        if (mDevice.getDiscoveryTransport().hasCloud()) {
            // Enumerate supported traits for clode devices
            probeDeviceTrait("onOff", DeviceAdapter.CardTypes.OnOffView);
            probeDeviceTrait("_mediaplayer", DeviceAdapter.CardTypes.MediaPlayerView);
            probeDeviceTrait("volume", DeviceAdapter.CardTypes.VolumeView);
        } else {
            // Assumed supported trails for local devices
            mCardsAdapter.add(DeviceAdapter.CardTypes.OnOffView);
            mCardsAdapter.add(DeviceAdapter.CardTypes.MediaPlayerView);
            mCardsAdapter.add(DeviceAdapter.CardTypes.VolumeView);
        }
    }

    private void probeDeviceTrait(final String trait, final DeviceAdapter.CardTypes type) {
        // Network call, punt off the main thread
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
                        Log.e(TAG, "Failure querying device state. " + result.getError());
                    } else {
                        Map<String, Object> state = (Map<String, Object>) result.getSuccess().getStateValue(trait);
                        if (state != null) {
                            Log.i(TAG, "Found " + trait + " trait, populating ...");
                            mCardsAdapter.add(type);
                        }
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
