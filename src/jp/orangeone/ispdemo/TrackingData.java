package jp.orangeone.ispdemo;

import java.text.DecimalFormat;
import java.util.Date;

import jp.co.isp21.Presence.PresenceManagerUtil;

import panda.lang.time.DateTimes;

public class TrackingData {
	private Date date;
	private int state = PresenceManagerUtil.PRESENCE_STATE_STOP;
	private double latitude = 0.0;
	private double longitude = 0.0;
	private String address;
	private float distance = 0.0f;
	private float speed = 0.0f;

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public int getState() {
		return state;
	}

	public void setState(int state) {
		this.state = state;
	}

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public float getDistance() {
		return distance;
	}

	public void setDistance(float distance) {
		this.distance = distance;
	}

	public float getSpeed() {
		return speed;
	}

	public void setSpeed(float speed) {
		this.speed = speed;
	}

	public String toMain() {
		return address;
	}

	public String toSub() {
		DecimalFormat df = new DecimalFormat("#.###");
		return DateTimes.datetimeFormat().format(date) + ' ' + IspHelper.getStateText(state)
				+ "\n(" + df.format(latitude) + ", " + df.format(longitude) + ") " 
				+ " - " + df.format(distance) + "m - " + df.format(speed) + "m/s";
	}
}
