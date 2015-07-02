package jp.orangeone.ispdemo;

import java.util.List;
import java.util.Locale;

import jp.co.isp21.Presence.PresenceManager;
import jp.co.isp21.Presence.PresenceManagerUtil;
import jp.co.isp21.Presence.service.PresenceService;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import panda.android.location.LocationChecker;
import panda.log.Log;
import panda.log.Logs;

public class MainActivity extends Activity implements OnClickListener {
	private static final Log log = Logs.getLog(MainActivity.class);

	private PresenceManager mPresenceManager;

	private LocationManager mLocationManager;

	private LocationChecker locationChecker = new LocationChecker();
	
	private String locationProvider = LocationManager.NETWORK_PROVIDER;
	
	private Handler mHandler;

	private Geocoder mGeocoder;

	private static class MyAdapter extends BaseAdapter {
		private Context context;
		private List<TrackingData> items;

		public MyAdapter(Context context, List<TrackingData> items) {
			this.context = context;
			this.items = items;
		}

		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public Object getItem(int position) {
			return items.get(getCount() - 1 - position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TwoLineListItem twoLineListItem;

			if (convertView == null) {
				LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				twoLineListItem = (TwoLineListItem)inflater.inflate(android.R.layout.simple_list_item_2, null);
				twoLineListItem.getText1().setTextSize(14);
				twoLineListItem.getText2().setTextSize(12);
			}
			else {
				twoLineListItem = (TwoLineListItem)convertView;
			}

			TextView text1 = twoLineListItem.getText1();
			TextView text2 = twoLineListItem.getText2();

			TrackingData td = (TrackingData)getItem(position);
			text1.setText(td.toMain());
			text2.setText(td.toSub());

			return twoLineListItem;
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findViewById(R.id.btnStart).setOnClickListener(this);
		findViewById(R.id.btnStop).setOnClickListener(this);

		buttonVisible(false);

		mHandler = new Handler(getMainLooper());

		mGeocoder = new Geocoder(this, Locale.getDefault());

		mLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

		// Criteriaオブジェクトを生成
		Criteria criteria = new Criteria();
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setPowerRequirement(Criteria.POWER_LOW);
		locationProvider = mLocationManager.getBestProvider(criteria, true);
		log.debug("Location Provider: " + locationProvider);
		
		setTitle(getTitle() + " - " + locationProvider);

		mPresenceManager = PresenceManager.getInstance(getApplicationContext());

		MyAdapter adapter = new MyAdapter(this, IspHelper.getTrackings());

		ListView listView = (ListView)findViewById(R.id.listTracking);
		listView.setAdapter(adapter);

		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				ListView listView = (ListView)parent;

				TrackingData item = (TrackingData)listView.getItemAtPosition(position);

				startActivity(new Intent(MainActivity.this, MapActivity.class));
			}
		});

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				IspHelper.loadTrackings();
				updateView();
			}
		}, 100);
	}

	public void onStart() {
		super.onStart();
	}

	private static BroadcastReceiver mIspReceiver = new IspReceiver();

	public void onResume() {
		super.onResume();
		buttonVisible(isPresenceServiceRunning());
	}

	public void onDestory() {
		super.onDestroy();
	}

	public void onStop() {
		super.onStop();
	}

	public void onPause() {
		super.onPause();
	}

	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnStart:
			startTrackingService();
			break;
		case R.id.btnStop:
			stopTrackingService();
			break;
		}
	}

	private LocationListener location = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
			if (!locationChecker.isFineLocation(location)) {
				log.debug("SKIP BAD " + location);
				return;
			}

			IspHelper.addLocation(location, mGeocoder);
			updateView();
		}

		@Override
		public void onProviderDisabled(String provider) {
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	};

	private void stopTrackingService() {
		getApplicationContext().unregisterReceiver(mIspReceiver);
		mPresenceManager.stopPresence();
		mLocationManager.removeUpdates(location);
		buttonVisible(false);
	}

	private void startTrackingService() {
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(PresenceManagerUtil.CHANGE_STATUS);
		getApplicationContext().registerReceiver(mIspReceiver, intentFilter);

		mPresenceManager.startPresence();
		mLocationManager.requestLocationUpdates(locationProvider,
			60000, // 通知のための最小時間間隔（ミリ秒）
			30, // 通知のための最小距離間隔（メートル）
			location);
		buttonVisible(true);
	}

	private void buttonVisible(boolean stop) {
		findViewById(R.id.btnStart).setEnabled(!stop);
		findViewById(R.id.btnStop).setEnabled(stop);
	}

	private void updateView() {
		ListView listView = (ListView)findViewById(R.id.listTracking);
		((MyAdapter)listView.getAdapter()).notifyDataSetChanged();
	}

	private boolean isPresenceServiceRunning() {
		ActivityManager am = (ActivityManager)getApplicationContext().getSystemService(Service.ACTIVITY_SERVICE);
		List<ActivityManager.RunningServiceInfo> runningServiceInfo = am.getRunningServices(Integer.MAX_VALUE);
		int serviceNum = runningServiceInfo.size();
		for (int i = 0; i < serviceNum; i++) {
			if (runningServiceInfo.get(i).service.getClassName().equals(PresenceService.class.getName())) {
				return true;
			}
		}
		return false;
	}

}
