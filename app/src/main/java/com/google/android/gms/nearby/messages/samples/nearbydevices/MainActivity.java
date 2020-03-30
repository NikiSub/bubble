package com.google.android.gms.nearby.messages.samples.nearbydevices;

import android.app.Activity;
import android.content.DialogInterface;
import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.messages.Message;
import com.google.android.gms.nearby.messages.MessageListener;
import com.google.android.gms.nearby.messages.Messages;
import com.google.android.gms.nearby.messages.PublishCallback;
import com.google.android.gms.nearby.messages.PublishOptions;
import com.google.android.gms.nearby.messages.Strategy;
import com.google.android.gms.nearby.messages.SubscribeCallback;
import com.google.android.gms.nearby.messages.SubscribeOptions;
import java.util.Calendar;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import java.util.UUID;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.widget.TextView;

/**
 * An activity that allows a user to publish device information, and receive information about
 * nearby devices.
 * <p/>
 * The UI exposes a button to subscribe to broadcasts from nearby devices, and another button to
 * publish messages that can be read nearby subscribing devices. Both buttons toggle state,
 * allowing the user to cancel a subscription or stop publishing.
 * <p/>
 * This activity demonstrates the use of the
 * {@link Messages#subscribe(GoogleApiClient, MessageListener, SubscribeOptions)},
 * {@link Messages#unsubscribe(GoogleApiClient, MessageListener)},
 * {@link Messages#publish(GoogleApiClient, Message, PublishOptions)}, and
 * {@link Messages#unpublish(GoogleApiClient, Message)} for foreground publication and subscription.
 * <p/>a
 * We check the app's permissions and present an opt-in dialog to the user, who can then grant the
 * required location permission.
 * <p/>
 * Using Nearby for in the foreground is battery intensive, and pub-sub is best done for short
 * durations. In this sample, we set the TTL for publishing and subscribing to three minutes
 * using a {@link Strategy}. When the TTL is reached, a publication or subscription expires.
 */
public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{

    protected LocationManager locationManager;
    protected LocationListener locationListener;
    protected Context context;
    String lat;
    String provider;
    protected String latitude, longitude;
    protected boolean gps_enabled, network_enabled, buttonPressed;

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int TTL_IN_SECONDS = 3 * 60; // Three minutes.

    // Key used in writing to and reading from SharedPreferences.
    private static final String KEY_UUID = "key_uuid";

    /**
     * Sets the time in seconds for a published message or a subscription to live. Set to three
     * minutes in this sample.
     */
    private static final Strategy PUB_SUB_STRATEGY = new Strategy.Builder().setDistanceType(Strategy.DISTANCE_TYPE_EARSHOT).setTtlSeconds(TTL_IN_SECONDS).build();
    RequestQueue queue;
    /**
     * Creates a UUID and saves it to {@link SharedPreferences}. The UUID is added to the published
     * message to avoid it being undelivered due to de-duplication. See {@link DeviceMessage} for
     * details.
     */
    private static String getUUID(SharedPreferences sharedPreferences) {
        String uuid = sharedPreferences.getString(KEY_UUID, "");
        if (TextUtils.isEmpty(uuid)) {
            uuid = UUID.randomUUID().toString();
            sharedPreferences.edit().putString(KEY_UUID, uuid).apply();
        }
        return uuid;
    }

    /**
     * The entry point to Google Play Services.
     */
    private GoogleApiClient mGoogleApiClient;
    private Vibrator vibrator;
    // Views.
    private ToggleButton mShareButton;
    private EditText mPhoneInput;
    private Button mSubmitButton;
    private Button mPanicButton;

    /**
     * The {@link Message} object used to broadcast information about the device to nearby devices.
     */
    private Message mPubMessage;

    /**
     * A {@link MessageListener} for processing messages from nearby devices.
     */
    private MessageListener mMessageListener;

    /**
     * Adapter for working with messages from nearby publishers.
     */

    private String phoneNumber;
    private String theirPhone;
    private Date currentTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buttonPressed = false;
        setContentView(R.layout.activity_main);

        vibrator = vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        queue = Volley.newRequestQueue(this);
        // Manager to get the device's location from
        // https://javapapers.com/android/get-current-location-in-android/
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
            return;
        }

        mShareButton = (ToggleButton) findViewById(R.id.publish_button);
        mPhoneInput = (EditText) findViewById(R.id.phone_Input);
        mSubmitButton = (Button) findViewById(R.id.submit_email);
        mPanicButton = (Button) findViewById((R.id.panicButton));

        // Build the message that is going to be published. This contains the device name and a
        // UUID.
        mPubMessage = DeviceMessage.newNearbyMessage("");
        phoneNumber = "";

        mMessageListener = new MessageListener() {
            @Override
            public void onFound(final Message message) {
                // Called when a new message is found.
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, mLocationListener);
                currentTime = Calendar.getInstance().getTime();
                theirPhone = DeviceMessage.fromNearbyMessage(message).getMessageBody();
                vibrator.vibrate(750);
                logInteraction();
            }

            @Override
            public void onLost(final Message message) {
                // Called when a message is no longer detectable nearby.
            }
        };

        mShareButton.setVisibility(View.GONE);
        mPanicButton.setVisibility(View.GONE);

        mSubmitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                phoneNumber = mPhoneInput.getText().toString();
                addUserGet(phoneNumber);
                if (phoneNumber != ""){
                    mShareButton.setVisibility(View.VISIBLE);
                    mPanicButton.setVisibility(View.VISIBLE);
                    mSubmitButton.setVisibility(View.GONE);
                    mPhoneInput.setVisibility(View.GONE);
                }
            }
        });

        final AlertDialog.Builder really = new AlertDialog.Builder(this)
                .setTitle("Bubble")
                .setMessage("Are you sure? Everyone you have been close to in the past few days will be notified. Please do not take this lightly.")
                // Specifying a listener allows you to take an action before dismissing the dialog.
                // The dialog is automatically dismissed when a dialog button is clicked.
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // Notify PPL
                        mPanicButton.setAlpha(.5f);
                        mPanicButton.setClickable(false);
                        dialog.cancel();
                        broadcastGet(phoneNumber);
                        mPanicButton.setText("Thank you! Stay safe and get well!");
                    }
                })
                // A null listener allows the button to dismiss the dialog and take no further action.
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        mPanicButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog a = really.create();
                a.show();
            }
        });

        mShareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected() && phoneNumber != "") {
                    mPubMessage = DeviceMessage.newNearbyMessage(mPhoneInput.getText().toString());
                    if (buttonPressed == false) {
                        subscribe();
                        publish();
                        buttonPressed = true;
                    } else {
                        unsubscribe();
                        unpublish();
                        buttonPressed = false;
                    }
                }
            }
        });

        buildGoogleApiClient();
    }

    public void logInteraction() {
        if (theirPhone == null || currentTime == null || latitude == null || longitude == null) {
            System.out.println(theirPhone);
            System.out.println(currentTime);
            System.out.println(latitude);
            System.out.println(longitude);
            return;
        }

        addNeighbor(phoneNumber,theirPhone,longitude,latitude,currentTime.toString());

        System.out.println("their phone: " +theirPhone);
        System.out.println("time received: " +currentTime);
        System.out.println("location: " +latitude+","+longitude);

        theirPhone = null;
        currentTime = null;
        latitude = null;
        longitude = null;
    }

    LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            locationManager.removeUpdates(mLocationListener);
            latitude = location.getLatitude() + "";
            longitude = location.getLongitude() + "";
            logInteraction();
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d("Latitude","disable");
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d("Latitude","enable");
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.d("Latitude","status");
        }
    };


    /**
     * Builds {@link GoogleApiClient}, enabling automatic lifecycle management using
     * {@link GoogleApiClient.Builder#enableAutoManage(FragmentActivity,
     * int, GoogleApiClient.OnConnectionFailedListener)}. I.e., GoogleApiClient connects in
     * {@link AppCompatActivity#onStart}, or if onStart() has already happened, it connects
     * immediately, and disconnects automatically in {@link AppCompatActivity#onStop}.
     */
    private void buildGoogleApiClient() {
        if (mGoogleApiClient != null) {
            return;
        }
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Nearby.MESSAGES_API)
                .addConnectionCallbacks(this)
                .enableAutoManage(this, this)
                .build();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        mShareButton.setEnabled(false);
        logAndShowSnackbar("Exception while connecting to Google Play services: " +
                connectionResult.getErrorMessage());
    }

    @Override
    public void onConnectionSuspended(int i) {
        logAndShowSnackbar("Connection suspended. Error code: " + i);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.i(TAG, "GoogleApiClient connected");
        // We use the Switch buttons in the UI to track whether we were previously doing pub/sub (
        // switch buttons retain state on orientation change). Since the GoogleApiClient disconnects
        // when the activity is destroyed, foreground pubs/subs do not survive device rotation. Once
        // this activity is re-created and GoogleApiClient connects, we check the UI and pub/sub
        // again if necessary.
        if (buttonPressed) {
            publish();
            subscribe();
        }
    }

    /**
     * Subscribes to messages from nearby devices and updates the UI if the subscription either
     * fails or TTLs.
     */
    void addUserGet(String pNumber){
        String url = "https://us-central1-bubble-07.cloudfunctions.net/addUser?text="+pNumber;
        StringRequest stringRequest = new StringRequest(Request.Method.GET,url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        //textView.setText("Response is: "+ response.substring(0,500));

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //textView.setText("That didn't work!");
            }
        });
        queue.add(stringRequest);
    }

    public void addNeighbor(String ourPhone, String theirPhone,String longitude, String latitude, String time){
        String url = "https://us-central1-bubble-07.cloudfunctions.net/addNeighbor?ours="+ourPhone+"&theirs="+theirPhone+"&long="+longitude+"&lat="+latitude+"&time="+time;
        StringRequest stringRequest = new StringRequest(Request.Method.GET,url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        //textView.setText("Response is: "+ response.substring(0,500));

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //textView.setText("That didn't work!");
            }
        });
        queue.add(stringRequest);
    }

    void broadcastGet(String pNumber){
        String url = "https://us-central1-bubble-07.cloudfunctions.net/broadcast?text="+pNumber+"&devmode=0";
        StringRequest stringRequest = new StringRequest(Request.Method.GET,url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        //textView.setText("Response is: "+ response.substring(0,500));

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //textView.setText("That didn't work!");
            }
        });
        queue.add(stringRequest);
    }

    private void subscribe() {
        Log.i(TAG, "Subscribing");
        SubscribeOptions options = new SubscribeOptions.Builder()
                .setStrategy(PUB_SUB_STRATEGY)
                .setCallback(new SubscribeCallback() {
                    @Override
                    public void onExpired() {
                        super.onExpired();
                        Log.i(TAG, "No longer subscribing");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                buttonPressed = false;
                            }
                        });
                    }
                }).build();

        Nearby.Messages.subscribe(mGoogleApiClient, mMessageListener, options)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Subscribed successfully.");
                        } else {
                            logAndShowSnackbar("Could not subscribe, status = " + status);
                            buttonPressed = false;
                        }
                    }
                });
    }

    /**
     * Publishes a message to nearby devices and updates the UI if the publication either fails or
     * TTLs.
     */
    private void publish() {
        Log.i(TAG, "Publishing");
        PublishOptions options = new PublishOptions.Builder()
                .setStrategy(PUB_SUB_STRATEGY)
                .setCallback(new PublishCallback() {
                    @Override
                    public void onExpired() {
                        super.onExpired();
                        Log.i(TAG, "No longer publishing");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                buttonPressed = false;
                            }
                        });
                    }
                }).build();

        Nearby.Messages.publish(mGoogleApiClient, mPubMessage, options)
                .setResultCallback(new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        if (status.isSuccess()) {
                            Log.i(TAG, "Published successfully.");
                        } else {
                            logAndShowSnackbar("Could not publish, status = " + status);
                            buttonPressed = false;
                        }
                    }
                });
    }

    /**
     * Stops subscribing to messages from nearby devices.
     */
    private void unsubscribe() {
        Log.i(TAG, "Unsubscribing.");
        Nearby.Messages.unsubscribe(mGoogleApiClient, mMessageListener);
    }

    /**
     * Stops publishing message to nearby devices.
     */
    private void unpublish() {
        Log.i(TAG, "Unpublishing.");
        Nearby.Messages.unpublish(mGoogleApiClient, mPubMessage);
    }

    /**
     * Logs a message and shows a {@link Snackbar} using {@code text};
     *
     * @param text The text used in the Log message and the SnackBar.
     */
    private void logAndShowSnackbar(final String text) {
        Log.w(TAG, text);
        View container = findViewById(R.id.activity_main_container);
        if (container != null) {
            Snackbar.make(container, text, Snackbar.LENGTH_LONG).show();
        }
    }
}