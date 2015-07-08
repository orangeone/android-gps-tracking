package jp.orangeone.gpstracking;

import jp.co.isp21.Presence.PresenceManagerUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PresenceReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		String ac = intent.getAction();
		if (ac.equals(PresenceManagerUtil.CHANGE_STATUS)) {
			int status = intent.getIntExtra(PresenceManagerUtil.NEW_STATUS, -1);
			GPSHelper.setState(status);
		}
	}
}
