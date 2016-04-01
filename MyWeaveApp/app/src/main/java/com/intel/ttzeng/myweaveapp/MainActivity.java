package com.intel.ttzeng.myweaveapp;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.apps.weave.apis.appaccess.AppAccessRequest;
import com.google.android.apps.weave.apis.data.ModelManifest;
import com.google.android.apps.weave.apis.data.WeaveApiClient;
import com.google.android.apps.weave.apis.data.WeaveDevice;
import com.google.android.apps.weave.apis.data.responses.Response;
import com.google.android.apps.weave.apis.data.responses.ResultCode;
import com.google.android.apps.weave.apis.device.DeviceLoaderCallbacks;
import com.google.android.apps.weave.framework.apis.Weave;

import java.util.concurrent.ConcurrentHashMap;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = "MainActivity";
    /**
     * Project number of a Google Cloud project with Weave API enabled and with proper
     * Android OAuth Client credentials associated with this app. Note that this is not
     * the project ID, but the project number. It should be a long integer number.
     */
    private static final String CLOUD_PROJECT_NUMBER = "985078386803";
    /**
     * Type of the devices that this app will request access for. Common types are
     * "developmentBoard", "vendor", "light" or "printer".
     * {@see <a href="https://developers.google.com/weave/v1/dev-guides/device-behavior/schema-library#uidevicekind">Weave docs</a>}
     */
    private static final String DEVICE_TYPE = "developmentBoard";

    private WeaveApiClient mApiClient;
    private DeviceListAdapter mDeviceListAdapter;
    private ConcurrentHashMap<String, ModelManifest> manifestCache;
    private final DeviceLoaderCallbacks mDiscoveryListener = new DeviceLoaderCallbacks() {
        @Override
        public void onDevicesFound(WeaveDevice[] weaveDevices) {
            for (final WeaveDevice device : weaveDevices) {
                Log.d(TAG, "Found device: " + device.getName() + "(" + device.getDescription() + ")\t" + device.getAccountName());
                addDevice(device);
            }
        }

        @Override
        public void onDevicesLost(WeaveDevice[] weaveDevices) {
            for (WeaveDevice device : weaveDevices) {
                Log.d(TAG, "Lost device: " + device.getName());
                mDeviceListAdapter.remove(device);
            }
            mDeviceListAdapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /* Create an API client instance */
        mApiClient = new WeaveApiClient(this);
        manifestCache = new ConcurrentHashMap<>();

        RecyclerView recyclerView = (RecyclerView)findViewById(R.id.devices_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        // Adapters provide a binding from an app-specific data set to views that are displayed within a RecyclerView
        mDeviceListAdapter = new DeviceListAdapter();
        recyclerView.setAdapter(mDeviceListAdapter);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestDeviceAccess();
            }
        });
    }

    @Override
    public void onPause() {
        stopDiscovery();
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void requestDeviceAccess() {
        AppAccessRequest request = new AppAccessRequest.Builder(
                AppAccessRequest.APP_ACCESS_ROLE_USER, DEVICE_TYPE, CLOUD_PROJECT_NUMBER).build();

        Response<Intent> accessResponse = Weave.APP_ACCESS_API.getRequestAccessIntent(mApiClient, request);
        if (accessResponse.isSuccess()) {
            Log.d(TAG, "Successfully created RequestAccessIntent: " + accessResponse.getSuccess());
            startActivityForResult(accessResponse.getSuccess(), 1);
        } else if (accessResponse.getError().getErrorCode() == ResultCode.RESOLUTION_REQUIRED) {
            Log.w(TAG, "Could not create RequestAccessIntent");
            // This is usually when the Weave Management app is
            // not installed. Firing the resolution Intent will
            // send the user to the Weave entry in Google Play Store.
            startActivityForResult(accessResponse.getError().getResolutionIntent(), 2);
        } else {
            Log.e(TAG, "Create RequestAccessIntent failure, no resolution intent provided. " +
                       "Error: " + accessResponse.getError());
            Snackbar.make(findViewById(R.id.devices_list), "Can't request access for devices", Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "Result onActivityResult: " + data + "  resultCode=" + resultCode + " requestCode=" + requestCode);
        switch (requestCode) {
            case 1:
                startDiscovery();
        }
    }

    /** Begins a scan for weave-accessible devices.  Searches for both cloud devices associated with
     * the user's account, and provisioned weave devices sitting on the same network.
     */
    public void startDiscovery() {
        Log.i(TAG, "startDiscovery");
        if (mApiClient != null) {
            Weave.DEVICE_API.startLoading(mApiClient, mDiscoveryListener);
        }
    }

    /**
     * Stops device discovery. Device discovery is battery intensive, so this should be called
     * as soon as further discovery is no longer needed.
     */
    public void stopDiscovery() {
        Log.i(TAG, "stopDiscovery");
        if (mApiClient != null) {
            Weave.DEVICE_API.stopLoading(mApiClient, mDiscoveryListener);
        }
    }

    private void addDevice(final WeaveDevice device) {
        /* To show more information (e.g. the device image and device type, etc.) to the user,
         * we need to fetch it off the main thread, because it will potentially trigger a network call.
         */
        new AsyncTask<Void, Void, ModelManifest>() {
            @Override
            protected ModelManifest doInBackground(Void... params) {
                String manifestId = device.getModelManifestId();
                ModelManifest manifest = manifestCache.get(manifestId);
                if (manifest == null) {
                    manifest = Weave.DEVICE_API.getModelManifest(mApiClient, manifestId)
                            .getSuccess();
                    if (manifest != null) {
                        manifestCache.put(manifestId, manifest);
                    }
                }
                return manifest;
            }

            @Override
            protected void onPostExecute(ModelManifest manifest) {
                mDeviceListAdapter.add(device, manifest);
                mDeviceListAdapter.notifyDataSetChanged();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
