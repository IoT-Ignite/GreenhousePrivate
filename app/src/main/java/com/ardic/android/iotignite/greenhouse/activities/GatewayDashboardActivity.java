package com.ardic.android.iotignite.greenhouse.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.ardic.android.iotignite.greenhouse.Constants;
import com.ardic.android.iotignite.greenhouse.CustomCardViewClickListener;
import com.ardic.android.iotignite.greenhouse.GatewayViewModel;
import com.ardic.android.iotignite.greenhouse.R;
import com.ardic.android.iotignite.greenhouse.RecyclerGatewayAdapter;
import com.ardic.android.iotignite.greenhouse.controllers.DROMController;
import com.ardic.android.iotignite.greenhouse.controllers.DeviceController;
import com.ardic.android.iotignite.greenhouse.listeners.DROMAsyncTaskListener;
import com.ardic.android.iotignite.greenhouse.listeners.DeviceAsyncTaskListener;
import com.ardic.android.iotignite.lib.restclient.model.Device;
import com.ardic.android.iotignite.lib.restclient.model.DeviceContent;
import com.wang.avi.AVLoadingIndicatorView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ardic.android.iotignite.greenhouse.Constants.CAMERA_PERMISSION_REQUEST;

public class GatewayDashboardActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, View.OnClickListener,
        CustomCardViewClickListener, SwipeRefreshLayout.OnRefreshListener, DeviceAsyncTaskListener,
        DROMAsyncTaskListener {

    private static final String TAG = GatewayDashboardActivity.class.getSimpleName();
    private FloatingActionButton fabAddGateway;
    private Toolbar toolbar;
    private ImageView mNoGwImageView;

    private RecyclerView recyclerView;
    private List<GatewayViewModel> gatewayList = new ArrayList<>();
    private DrawerLayout drawer;
    private ActionBarDrawerToggle toggle;
    private NavigationView navigationView;
    private LinearLayoutManager layoutManager;
    private RecyclerGatewayAdapter recyclerGatewayAdapter;
    private SwipeRefreshLayout gatewaySwipeRefreshLayout;
    private DROMController mDromController;
    private DeviceController mDeviceController;
    private Device devices;
    private String deviceId;
    private AVLoadingIndicatorView loadingIndicator;


    private Handler dromDeviceHandler = new Handler();
    private int dromDeviceTryCount = 0;
    private Runnable dromDeviceRunnable = new Runnable() {

        @Override
        public void run() {


            if (dromDeviceTryCount < Constants.DROM_TRY_COUNT) {
                if (!updateDevice()) {
                    Log.i(TAG, "Waiting Device...");
                    showLoadingProgress(true);
                    dromDeviceTryCount++;
                    dromDeviceHandler.postDelayed(this, 3000L);
                } else {
                    dromDeviceHandler.removeCallbacks(this);
                    updateDashboard();
                    deviceId = null;
                    dromDeviceTryCount = 0;
                    // success
                    GatewayDashboardActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showLoadingProgress(false);
                            Toast.makeText(GatewayDashboardActivity.this, "AWESOME ! DEVICE LICENCED SUCCESSFULLY. ", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            } else {
                Toast.makeText(GatewayDashboardActivity.this, "DROM LICENCED FAILURE !!", Toast.LENGTH_LONG).show();
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gateway_dashboard);


        initUI();
        // get previous configured gateways..
        updateDashboard();

    }

    private void initUI() {

        toolbar = (Toolbar) findViewById(R.id.app_bar_gateway_dashboard_toolbar);
        setSupportActionBar(toolbar);

        fabAddGateway = (FloatingActionButton) findViewById(R.id.gateway_fab);
        fabAddGateway.setOnClickListener(this);

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        drawer = (DrawerLayout) findViewById(R.id.activity_gateway_dashboard_drawer_layout);

        toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        // drawer.addDrawerListener(toggle);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        layoutManager.scrollToPosition(0);

        recyclerView = (RecyclerView) findViewById(R.id.content_gateway_dashboard_recycler_view);
        recyclerView.setLayoutManager(layoutManager);

        gatewayList = new ArrayList<>();
        recyclerGatewayAdapter = new RecyclerGatewayAdapter(gatewayList, this);

        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(recyclerGatewayAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        gatewaySwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.app_bar_gateway_swipe_refresh_layout);
        gatewaySwipeRefreshLayout.setOnRefreshListener(this);

        loadingIndicator = (AVLoadingIndicatorView) findViewById(R.id.progress);

        mNoGwImageView = (ImageView) findViewById(R.id.no_gateway_image_view);

    }

    @Override
    public void onBackPressed() {
        drawer = (DrawerLayout) findViewById(R.id.activity_gateway_dashboard_drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {
            //TODO :
        } else if (id == R.id.nav_slideshow) {
            //TODO :
        } else if (id == R.id.nav_manage) {
            //TODO :
        } else if (id == R.id.nav_share) {
            //TODO :
        } else if (id == R.id.nav_send) {
            //TODO :
        }

        drawer = (DrawerLayout) findViewById(R.id.activity_gateway_dashboard_drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onClick(View view) {

        /**
         * Start QR Scan Activity here.
         */
        if (view.equals(fabAddGateway)) {

            //TODO : Check camera permission here. - RunTime and Manifest.

            Snackbar.make(view, "Scan your QR code to register your gateway.", Snackbar.LENGTH_SHORT)
                    .addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {

                        @Override
                        public void onDismissed(Snackbar transientBottomBar, int event) {
                            super.onDismissed(transientBottomBar, event);
//TODO checkCameraPermission
                            checkCameraPermission();
                        }

                        @Override
                        public void onShown(Snackbar transientBottomBar) {
                            super.onShown(transientBottomBar);
                        }
                    }).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode == RESULT_OK && Constants.Actions.ACTION_GW_QR_CODE_SUCCESS.equals(data.getAction())) {

            String qr = data.getStringExtra(Constants.Extra.EXTRA_GW_QR_CODE);
            Log.i(TAG, "QR Code Received !" + qr);
            Toast.makeText(this, "QR Code Received !" + qr, Toast.LENGTH_LONG).show();

            showLoadingProgress(true);
            mDromController = new DROMController(this, qr, this);

            mDromController.execute();

            deviceId = qr;
        } else {
            Toast.makeText(this, "QR Code Failure Please Try Again...", Toast.LENGTH_LONG).show();
        }
    }

    private synchronized void getDeviceInfo() {

        showLoadingProgress(true);
        mDeviceController = new DeviceController(this, this);

        mDeviceController.execute();
    }

    private boolean updateDevice() {
        getDeviceInfo();
        return checkDevice();
    }

    @Override
    public void onItemClick(View v, int position) {
        Log.i(TAG, "Position on recycler view:" + position);
        GatewayViewModel gateway = gatewayList.get(position);

        if (gateway != null && !TextUtils.isEmpty(gateway.getGatewayId())) {
            Toast.makeText(getApplicationContext(), " Position: " + position + " Gateway ID: " + gateway.getGatewayId(), Toast.LENGTH_SHORT).show();
            Intent startSensorDashboardActivity = new Intent(GatewayDashboardActivity.this, SensorDashboardActivity.class);
            startSensorDashboardActivity.putExtra(Constants.Extra.EXTRA_DEVICE_ID, gateway.getGatewayId());
            startSensorDashboardActivity.putExtra(Constants.Extra.EXTRA_DEVICE_CODE, getDeviceCodeById(gateway.getGatewayId()));
            startActivity(startSensorDashboardActivity);
        }
    }

    /**
     * Called when a swipe gesture triggers a refresh.
     */
    @Override
    public void onRefresh() {
        updateDashboard();
    }

    private void updateDashboard() {
        getDeviceInfo();
    }

    private void updateGatewayList(Device device) {
        gatewayList.clear();

        if (device != null && device.getDeviceContents() != null) {
            for (DeviceContent cnt : device.getDeviceContents()) {
                gatewayList.add(new GatewayViewModel(cnt.getLabeL(), cnt.getDeviceId(), Constants.ONLINE_DEVICE.equals(cnt.getPresence().getState()) ? true : false));
            }
            recyclerGatewayAdapter.notifyDataSetChanged();
        }
    }

    private boolean checkDevice() {
        for (GatewayViewModel mdl : gatewayList) {
            if (mdl.getGatewayId().equals(deviceId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onDeviceTaskComplete(Device mDevice) {

        Log.i(TAG, "Task Complete .\n " + mDevice);
        if (mDevice != null) {
            devices = mDevice;
            updateGatewayList(devices);
        }
        gatewaySwipeRefreshLayout.setRefreshing(false);
        showLoadingProgress(false);
        setNoGwImage();

    }

    @Override
    public void onDromTaskComplete(boolean result) {

        showLoadingProgress(false);
        if (result) {
            dromDeviceHandler.postDelayed(dromDeviceRunnable, 2000L);
        } else {
            Toast.makeText(GatewayDashboardActivity.this, "DROM LICENCED FAILURE PLEASE TRY AGAIN !!", Toast.LENGTH_LONG).show();
        }
    }


    private void showLoadingProgress(final boolean state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (loadingIndicator != null) {
                    if (state) {
                        loadingIndicator.show();
                    } else {
                        loadingIndicator.hide();
                    }
                }
            }
        });
    }

    private String getDeviceCodeById(String deviceId) {

        String deviceCode = null;
        for (DeviceContent content : devices.getDeviceContents()) {

            if (deviceId.equals(content.getDeviceId())) {
                deviceCode = content.getCode();
            }
        }

        return deviceCode;
    }


    private void setNoGwImage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (gatewayList != null && gatewayList.isEmpty()) {
                    Log.i(TAG, "Setting no gw image.");
                    mNoGwImageView.setVisibility(View.VISIBLE);
                } else {
                    mNoGwImageView.setVisibility(View.INVISIBLE);
                }
            }
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSION_REQUEST: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startQRActivity();
                } else {
                    Toast.makeText(this, "Camera permission required for read QR CODE!!!", Toast.LENGTH_LONG).show();
                }
            }
        }
    }


    private void checkCameraPermission() {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(GatewayDashboardActivity.this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST
            );

        } else {
            startQRActivity();
        }
    }

    private void startQRActivity() {
        Intent intent = new Intent(GatewayDashboardActivity.this, QRScanActivity.class);
        intent.setAction(Constants.Actions.ACTION_GW_QR_CODE);
        startActivityForResult(intent, Constants.READ_QR_CODE);
    }

    @Override
    protected void onDestroy() {
        if (drawer != null) {
            drawer.removeDrawerListener(toggle);
        }
        super.onDestroy();
    }
}

