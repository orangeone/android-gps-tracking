package jp.orangeone.gpstracking;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jp.co.isp21.Presence.PresenceManagerUtil;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Environment;

import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import panda.bind.json.Jsons;
import panda.io.FileNames;
import panda.io.Files;
import panda.io.LineIterator;
import panda.io.Streams;
import panda.lang.Charsets;
import panda.lang.Collections;
import panda.lang.Strings;
import panda.lang.time.DateTimes;
import panda.log.Log;
import panda.log.Logs;

public class GPSHelper {
	private static final Log log = Logs.getLog(GPSHelper.class);

	private static List<TrackingData> trackings = new ArrayList<TrackingData>();

	@SuppressLint("UseSparseArrays")
	private static Map<Integer, Long> states = new HashMap<Integer, Long>();
	private static int lastState;
	private static long lastStart;

	public static List<TrackingData> getTrackings() {
		return trackings;
	}

	public static String getTrackingFile(Calendar c) {
		String fn = GPSHelper.class.getPackage().getName() + "/gpstracking." + DateTimes.dateLogFormat().format(c)
				+ ".txt";
		return FileNames.concat(Environment.getExternalStorageDirectory().getAbsolutePath(), fn);
	}

	public static void backward() {
		try {
			File od = new File(FileNames.concat(Environment.getExternalStorageDirectory().getAbsolutePath(), "ispdemo"));
			File nd = new File(FileNames.concat(Environment.getExternalStorageDirectory().getAbsolutePath(),
				GPSHelper.class.getPackage().getName()));
			if (Files.isDirectory(od)) {
				Collection<File> fs = Files.listFiles(od, "txt");
				if (Collections.isNotEmpty(fs)) {
					Files.makeDirs(nd);
					for (File f : fs) {
						String nn = Strings.replace(f.getAbsolutePath(), "/ispdemo/ispdemo.", "/"
								+ GPSHelper.class.getPackage().getName() + "/gpstracking.");
						log.debug("move " + f.getAbsolutePath() + " -> " + nn);
						Files.moveFile(f, new File(nn));
					}
				}
			}
		}
		catch (IOException e) {
			log.warn("backward error", e);
		}
	}

	public static void loadTrackings(Calendar c) {
		backward();

		trackings.clear();

		File file = new File(getTrackingFile(c));
		if (!file.exists()) {
			log.warn(file + "does not exist");
			return;
		}

		log.info("Loading " + file);

		float[] results = new float[1];
		LineIterator li = null;
		try {
			li = Files.lineIterator(file);
			while (li.hasNext()) {
				String line = li.next();
				if (Strings.isEmpty(line)) {
					continue;
				}

				TrackingData td = Jsons.fromJson(line, TrackingData.class);
				TrackingData ltd = getLastLocation();
				if (ltd != null) {
					Location.distanceBetween(ltd.getLatitude(), ltd.getLongitude(), td.getLatitude(),
						td.getLongitude(), results);
					td.setDistance(results[0]);
					td.setSpeed(td.getDistance() / DateTimes.subSeconds(td.getDate(), ltd.getDate()));
				}
				trackings.add(td);
			}
		}
		catch (IOException e) {
			log.error(e);
		}
		finally {
			Streams.safeClose(li);
		}
	}

	public static TrackingData getLastLocation() {
		if (Collections.isNotEmpty(trackings)) {
			return trackings.get(trackings.size() - 1);
		}
		return null;
	}

	public static TrackingData getFirstLocation() {
		if (Collections.isNotEmpty(trackings)) {
			return trackings.get(0);
		}
		return null;
	}

	public static boolean addLocation(Location location, Geocoder geocoder) {
		TrackingData ltd = getLastLocation();

		float distance = 0.0f;
		float speed = 0.0f;

		if (ltd != null) {
			float[] results = new float[1];
			Location.distanceBetween(ltd.getLatitude(), ltd.getLongitude(), location.getLatitude(),
				location.getLongitude(), results);

			distance = results[0];
			if (distance < 30) {
				log.debug("SKIP SAME " + location + ": " + distance);
				return false;
			}

			long delta = DateTimes.subSeconds(new Date(location.getTime()), ltd.getDate());
			// less than 30 minutes
			if (delta < 30 * 60) {
				speed = distance / delta;
				// great than 180km/h
				if (speed >= 50) {
					log.debug("SKIP FAST " + location + ": " + distance);
					return false;
				}
			}
		}

		String address = "";
		try {
			List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
			if (Collections.isNotEmpty(addresses)) {
				Address a = addresses.get(0);
				address = toAddress(a);
			}

			// if (ltd != null && Strings.isNotEmpty(address)) {
			// if (ltd.getAddress().equals(address)) {
			// log.debug("Skip (" + location + "): " + address);
			// return;
			// }
			// }
		}
		catch (Exception e) {
			// Catch network or other I/O problems.
			log.error("Failed to get address of (" + location.getLatitude() + ", " + location.getLongitude(), e);
		}

		TrackingData td = new TrackingData();
		td.setDate(new Date(location.getTime()));
		td.setState(getBestState());
		td.setLatitude(location.getLatitude());
		td.setLongitude(location.getLongitude());
		td.setAddress(address);

		if (ltd != null) {
			float[] results = new float[1];
			Location.distanceBetween(ltd.getLatitude(), ltd.getLongitude(), td.getLatitude(), td.getLongitude(),
				results);
			td.setDistance(results[0]);
			td.setSpeed(td.getDistance() / DateTimes.subSeconds(td.getDate(), ltd.getDate()));
		}

		trackings.add(td);
		saveTrackingData(td);

		return true;
	}

	private static void saveTrackingData(TrackingData td) {
		String s = Jsons.toJson(td);

		Writer r = null;
		try {
			log.info("ADD: " + s);

			File file = new File(getTrackingFile(Calendar.getInstance()));
			r = new OutputStreamWriter(new FileOutputStream(file, true), Charsets.UTF_8);
			r.append(s);
			r.append(Streams.LINE_SEPARATOR);
		}
		catch (IOException e) {
			log.error("Failed to save " + s, e);
		}
		finally {
			Streams.safeClose(r);
		}
	}

	private static int getBestState() {
		if (Collections.isEmpty(states)) {
			return PresenceManagerUtil.PRESENCE_STATE_STOP;
		}

		long max = 0;
		for (Long time : states.values()) {
			if (time > max) {
				max = time;
			}
		}

		int state = PresenceManagerUtil.PRESENCE_STATE_STOP;
		for (Entry<Integer, Long> en : states.entrySet()) {
			if (max == en.getValue()) {
				state = en.getKey();
				break;
			}
		}

		states.clear();

		states.put(lastState, 0L);
		lastStart = System.currentTimeMillis();

		return state;
	}

	public static void setState(int state) {
		log.debug("ADD state: " + getStateText(state));

		if (Collections.isEmpty(states)) {
			states.put(state, 0L);
			lastState = state;
			lastStart = System.currentTimeMillis();
			return;
		}

		Long ltime = states.get(lastState);
		if (ltime != null) {
			ltime += (System.currentTimeMillis() - lastStart);
			states.put(lastState, ltime);
		}

		lastState = state;
		lastStart = System.currentTimeMillis();

		if (!states.containsKey(state)) {
			states.put(state, 0L);
		}
	}

	public static String toAddress(Address a) {
		StringBuilder sb = new StringBuilder();
		// if (Strings.isNotEmpty(a.getPostalCode())) {
		// sb.append(a.getPostalCode()).append(' ');
		// }
		// if (Strings.isNotEmpty(a.getCountryName())) {
		// sb.append(a.getCountryName()).append(' ');
		// }
		// if (Strings.isNotEmpty(a.getAdminArea())) {
		// sb.append(a.getAdminArea()).append(' ');
		// }
		// if (Strings.isNotEmpty(a.getSubAdminArea())) {
		// sb.append(a.getSubAdminArea()).append(' ');
		// }
		// if (Strings.isNotEmpty(a.getLocality())) {
		// sb.append(a.getLocality()).append(' ');
		// }
		// if (Strings.isNotEmpty(a.getSubLocality())) {
		// sb.append(a.getSubLocality()).append(' ');
		// }
		// if (Strings.isNotEmpty(a.getThoroughfare())) {
		// sb.append(a.getThoroughfare()).append(' ');
		// }
		for (int i = 0; i <= a.getMaxAddressLineIndex(); i++) {
			String line = a.getAddressLine(i);
			if (Strings.isEmpty(line)) {
				continue;
			}
			sb.append(line).append(' ');
		}
		return sb.toString().trim();
	}

	public static String getStateText(int status) {
		switch (status) {
		case PresenceManagerUtil.PRESENCE_STATE_REST:
			return "REST";
		case PresenceManagerUtil.PRESENCE_STATE_STOP:
			return "STOP";
		case PresenceManagerUtil.PRESENCE_STATE_WALK:
			return "WALK";
		case PresenceManagerUtil.PRESENCE_STATE_RUN:
			return "RUN";
		case PresenceManagerUtil.PRESENCE_STATE_VEHICLE:
			return "VEHICLE";
		default:
			return "UNKNOWN";
		}
	}

	public static int getStateColor(int status) {
		switch (status) {
		case PresenceManagerUtil.PRESENCE_STATE_REST:
			return Color.GRAY;
		case PresenceManagerUtil.PRESENCE_STATE_STOP:
			return Color.DKGRAY;
		case PresenceManagerUtil.PRESENCE_STATE_WALK:
			return Color.GREEN;
		case PresenceManagerUtil.PRESENCE_STATE_RUN:
			return Color.BLUE;
		case PresenceManagerUtil.PRESENCE_STATE_VEHICLE:
			return Color.MAGENTA;
		default:
			return Color.WHITE;
		}
	}

	public static BitmapDescriptor getStateIcon(int status) {
		switch (status) {
		case PresenceManagerUtil.PRESENCE_STATE_REST:
			return BitmapDescriptorFactory.fromResource(R.drawable.rest);
		case PresenceManagerUtil.PRESENCE_STATE_STOP:
			return BitmapDescriptorFactory.fromResource(R.drawable.stop);
		case PresenceManagerUtil.PRESENCE_STATE_WALK:
			return BitmapDescriptorFactory.fromResource(R.drawable.walk);
		case PresenceManagerUtil.PRESENCE_STATE_RUN:
			return BitmapDescriptorFactory.fromResource(R.drawable.run);
		case PresenceManagerUtil.PRESENCE_STATE_VEHICLE:
			return BitmapDescriptorFactory.fromResource(R.drawable.vehicle);
		default:
			return BitmapDescriptorFactory.fromResource(R.drawable.stop);
		}
	}

	// http://developer.android.com/training/location/activity-recognition.html
	/**
	 * Map detected activity types to strings
	 * 
	 * @param activityType The detected activity type
	 * @return A user-readable name for the type
	 */
	public static String getActivityText(int activityType) {
		switch (activityType) {
		case DetectedActivity.IN_VEHICLE:
			return "in_vehicle";
		case DetectedActivity.ON_BICYCLE:
			return "on_bicycle";
		case DetectedActivity.ON_FOOT:
			return "on_foot";
		case DetectedActivity.STILL:
			return "still";
		case DetectedActivity.UNKNOWN:
			return "unknown";
		case DetectedActivity.TILTING:
			return "tilting";
		default:
			return "unknown(" + activityType + ")";
		}
	}
}
