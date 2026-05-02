package com.example.myapplication2;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.volley.toolbox.JsonArrayRequest;
import org.json.JSONArray;

import io.socket.client.IO;
import io.socket.client.Socket;
import java.net.URISyntaxException;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.ArrayAdapter;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import org.json.JSONException;
import org.json.JSONObject;
import android.util.Log;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    // Client to interact with the location API
    private FusedLocationProviderClient fusedLocationClient;
    // An arbitrary constant to track our permission request
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private Socket mSocket;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Ensure this matches your XML file name

        // 1. Initialize the FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Map is ready, now check permissions and get location
        enableUserLocation();
        googleMap.setTrafficEnabled(true);

        // FETCH ALL SAVED MARKERS THE MOMENT THE MAP LOADS
        fetchMarkersFromServer(googleMap);

        try{
            mSocket = IO.socket("http://13.235.88.133:3000");
            mSocket.connect();

        }catch(URISyntaxException e){
            e.printStackTrace();

        }
        mSocket.on("newMarker", args -> {
            runOnUiThread(() -> {
                try {
                    JSONObject data = (JSONObject) args[0];
                    double lat = data.getDouble("latitude");
                    double lon = data.getDouble("longitude");
                    String note = data.getString("note");
                    String type = data.optString("type", "HeavyTraffic");
                    BitmapDescriptor icon ;
                    switch (type) {
                        case "Accident":
                            icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_accident);
                            break;
                        case "Construction":
                            icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_construction);
                            break;
                        default:
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
                            break;
                    }
                }catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        })
        // Notice we even used a lambda for the LongClickListener here!
        googleMap.setOnMapLongClickListener(latLng -> {

            // 1. Create a container layout to hold both inputs
            LinearLayout container = new LinearLayout(MainActivity.this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(50, 20, 50, 0);

            // 2. Set up the Note Input
            final EditText inputBox = new EditText(MainActivity.this);
            inputBox.setHint("e.g., Traffic backed up...");
            container.addView(inputBox);

            // 3. Set up the Type Dropdown (Spinner)
            final Spinner typeSpinner = new Spinner(MainActivity.this);
            String[] types = {"Accident", "Construction", "Heavy Traffic"};
            ArrayAdapter<String> adapter = new ArrayAdapter<>(MainActivity.this,
                    android.R.layout.simple_spinner_item, types);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            typeSpinner.setAdapter(adapter);
            container.addView(typeSpinner);

            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Report Traffic Alert")
                    .setMessage("Select type and add note:")
                    .setView(container)

                    // --- CLEAN LAMBDA FOR POSITIVE BUTTON ---
                    .setPositiveButton("Add Marker", (dialog, whichButton) -> {

                        String userNote = inputBox.getText().toString();
                        String selectedType = typeSpinner.getSelectedItem().toString();

                        // 1. Create the JSON data payload
                        JSONObject markerData = new JSONObject();
                        try {
                            markerData.put("latitude", latLng.latitude);
                            markerData.put("longitude", latLng.longitude);
                            markerData.put("note", userNote);
                            markerData.put("type", selectedType); // Save the type to MongoDB!
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
                        // REMEMBER TO PUT YOUR AWS ELASTIC IP HERE
                        String url = "http://13.235.88.133:3000/markers";

                        JsonObjectRequest postRequest = new JsonObjectRequest(Request.Method.POST, url, markerData,
                                response -> {
                                    Toast.makeText(MainActivity.this, "Saved to Cloud!", Toast.LENGTH_SHORT).show();

                                    // Pick the right icon to drop on the screen immediately
                                    BitmapDescriptor icon;
                                    switch (selectedType) {
                                        case "Accident":
                                            icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_accident);
                                            break;
                                        case "Construction":
                                            icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_construction);
                                            break;
                                        default:
                                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
                                            break;
                                    }

                                    googleMap.addMarker(new MarkerOptions()
                                            .position(latLng)
                                            .title(selectedType + " Alert")
                                            .snippet(userNote)
                                            .icon(icon)); // Drop with custom icon
                                },
                                error -> {
                                    String exactError = error.toString();
                                    Log.e("NetworkError", "Error: " + exactError);
                                    Toast.makeText(MainActivity.this, "Error: " + exactError, Toast.LENGTH_LONG).show();
                                }
                        );

                        queue.add(postRequest);
                    })

                    // --- CLEAN LAMBDA FOR NEGATIVE BUTTON ---
                    .setNegativeButton("Cancel", (dialog, whichButton) -> {
                        dialog.dismiss(); // Just quietly close the box
                    })
                    .show();
        });
    }

    private void fetchMarkersFromServer(GoogleMap googleMap) {
        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
        String url = "http://13.235.88.133:3000/markers";
        JsonArrayRequest getRequest = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
            try{
                for(int i=0; i<response.length(); i++) {
                    JSONObject mar = response.getJSONObject(i);
                    double lat = mar.getDouble("latitude");
                    double lon = mar.getDouble("longitude");
                    String note = mar.getString("note");
                    String type = mar.optString("type", "HeavyTraffic");

                    BitmapDescriptor icon;
                    switch (type) {
                        case "Accident":
                            icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_accident);
                            break;
                        case "Construction":
                            icon = BitmapDescriptorFactory.fromResource(R.drawable.ic_construction);
                            break;
                        default:
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
                            break;
                    }

                    googleMap.addMarker(new MarkerOptions()
                            .position(new LatLng(lat, lon))
                            .title(type + "alert")
                            .snippet(note)
                            .icon(icon));
                    }

                }catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Error: "+ e.toString(), Toast.LENGTH_LONG).show();

                }
            },error -> {
                String exactError = error.toString();
                Log.e("NetworkError", "Error: " + exactError);
                Toast.makeText(MainActivity.this, "Error: " + exactError, Toast.LENGTH_LONG).show();
            }

        );

        queue.add(getRequest);
    }

    private void enableUserLocation() {
        // 2. Check if the user has already granted location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            // Permission is granted: enable the default "blue dot"
            mMap.setMyLocationEnabled(true);

            // 3. Get the last known location
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations, this can be null.
                            if (location != null) {
                                // Extract latitude and longitude
                                LatLng currentLocation = new LatLng(location.getLatitude(), location.getLongitude());

                                // Place a marker and zoom the camera
                                mMap.addMarker(new MarkerOptions().position(currentLocation).title("You are here"));
                                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 15f));
                            } else {
                                Toast.makeText(MainActivity.this, "Location not found. Try opening Google Maps first to cache a location.", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        } else {
            // 4. Permission is not granted: request it from the user
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    // 5. Handle the result of the permission dialog
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // The user clicked "Allow"
                enableUserLocation();
            } else {
                // The user clicked "Deny"
                Toast.makeText(this, "Location permission denied. Cannot show your current location on the map.", Toast.LENGTH_LONG).show();
            }
        }
    }
}