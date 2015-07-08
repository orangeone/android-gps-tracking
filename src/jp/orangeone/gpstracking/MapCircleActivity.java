package jp.orangeone.gpstracking;

import java.util.List;

import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapLoadedCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import panda.log.Log;
import panda.log.Logs;

public class MapCircleActivity extends FragmentActivity {
	private static final Log log = Logs.getLog(MapCircleActivity.class);
	
	private GoogleMap googleMap;
	private Handler mainHandler;
	private int index;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_map_line);
		setUpMapIfNeeded();
	}

	private void setUpMapIfNeeded() {
		// check if we have got the googleMap already
		if (googleMap == null) {
			googleMap = ((SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
			if (googleMap != null) {
				mainHandler = new Handler(getMainLooper());
				
				TrackingData td = GPSHelper.getFirstLocation();
				if (td != null) {
					moveCamera(td);
				}

				googleMap.setOnMapLoadedCallback(new OnMapLoadedCallback() {
					@Override
					public void onMapLoaded() {
						mainHandler.post(new DrawLine());
					}
				});
			}
		}
	}

	private class DrawLine implements Runnable {
		@Override
		public void run() {
			try {
				List<TrackingData> tds = GPSHelper.getTrackings();
				if (index > tds.size() - 2) {
					return;
				}
				
				drawLine(tds.get(index), tds.get(++index));
				mainHandler.postDelayed(new DrawLine(), 500);
			}
			catch (Throwable e) {
				log.error(e);
			}
		}
	}

	private void moveCamera(TrackingData td) {
		LatLng ll = new LatLng(td.getLatitude(), td.getLongitude());
		googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 13));
	}
	
	private void drawLine(TrackingData start, TrackingData end) {
		LatLng lls = new LatLng(start.getLatitude(), start.getLongitude());
		LatLng lle = new LatLng(end.getLatitude(), end.getLongitude());
		BitmapDescriptor icon = BitmapDescriptorFactory.fromResource(R.drawable.circle);

		log.debug("Draw Circle " + lls + " -> " + lle);
		googleMap.addPolyline(new PolylineOptions().add(lls, lle).width(5).color(GPSHelper.getStateColor(end.getState())).geodesic(true));
		googleMap.addMarker(new MarkerOptions().position(lls).icon(icon));
		googleMap.animateCamera(CameraUpdateFactory.newLatLng(lle));
	}
}
