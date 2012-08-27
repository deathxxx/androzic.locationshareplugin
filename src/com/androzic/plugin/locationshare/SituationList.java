package com.androzic.plugin.locationshare;

import com.androzic.data.Situation;
import com.androzic.util.Geo;
import com.androzic.util.StringFormatter;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.ToggleButton;

public class SituationList extends ListActivity implements OnSharedPreferenceChangeListener
{
	private static final String TAG = "SituationList";

	private SituationListAdapter adapter;
	public SharingService sharingService = null;

	private int timeoutInterval = 600; // 10 minutes (default)

	private ToggleButton toggle;

	@Override
	public void onCreate(final Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_userlist);

		TextView emptyView = (TextView) getListView().getEmptyView();
		if (emptyView != null)
			emptyView.setText(R.string.msg_empty_list);

		//TODO Check session and user are set
		toggle = (ToggleButton) findViewById(R.id.enable_toggle);

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		onSharedPreferenceChanged(sharedPreferences, getString(R.string.pref_sharing_timeout));
		sharedPreferences.registerOnSharedPreferenceChangeListener(this);

		adapter = new SituationListAdapter(this);
		setListAdapter(adapter);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		if (isServiceRunning())
		{
			toggle.setChecked(true);
			connect();
		}
	}

	@Override
	public void onPause()
	{
		super.onPause();
		disconnect();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.preferences, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.menuPreferences:
				startActivity(new Intent(this, Preferences.class));
				return true;
		}
		return false;
	}

	public void onToggleEnable(View view)
	{
		if (toggle.isChecked() && !isServiceRunning())
		{
			startService(new Intent(this, SharingService.class));
			connect();
		}
		else if (!toggle.isChecked() && isServiceRunning())
		{
			disconnect();
			stopService(new Intent(this, SharingService.class));
		}
	}

	private void connect()
	{
		bindService(new Intent(this, SharingService.class), sharingConnection, 0);
	}

	private void disconnect()
	{
		if (sharingService != null)
		{
			unregisterReceiver(sharingReceiver);
			unbindService(sharingConnection);
			sharingService = null;
			TextView title = (TextView) findViewById(R.id.title_text);
			title.setText("");
			adapter.notifyDataSetChanged();
		}
	}

	private boolean isServiceRunning()
	{
		ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
		{
			if ("com.androzic.plugin.locationshare.SharingService".equals(service.service.getClassName()))
				return true;
		}
		return false;
	}

	private ServiceConnection sharingConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service)
		{
			sharingService = ((SharingService.LocalBinder) service).getService();
			registerReceiver(sharingReceiver, new IntentFilter(SharingService.BROADCAST_SITUATION_CHANGED));
			runOnUiThread(new Runnable() {
				public void run()
				{
					TextView title = (TextView) findViewById(R.id.title_text);
					title.setText(sharingService.user + " ∈ " + sharingService.session);
					adapter.notifyDataSetChanged();
				}
			});
			Log.d(TAG, "Sharing service connected");
		}

		public void onServiceDisconnected(ComponentName className)
		{
			sharingService = null;
			TextView title = (TextView) findViewById(R.id.title_text);
			title.setText("");
			adapter.notifyDataSetChanged();
			Log.d(TAG, "Sharing service disconnected");
		}
	};

	private BroadcastReceiver sharingReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent)
		{
			Log.e(TAG, "Broadcast: " + intent.getAction());
			if (intent.getAction().equals(SharingService.BROADCAST_SITUATION_CHANGED))
			{
				runOnUiThread(new Runnable() {
					public void run()
					{
						adapter.notifyDataSetChanged();
					}
				});
			}
		}
	};

	public class SituationListAdapter extends BaseAdapter
	{
		private LayoutInflater mInflater;
		private int mItemLayout;

		public SituationListAdapter(Context context)
		{
			mItemLayout = R.layout.situation_list_item;
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		}

		public Situation getItem(int position)
		{
			if (sharingService != null)
				return sharingService.situationList.get(position);
			else
				return null;
		}

		@Override
		public long getItemId(int position)
		{
			if (sharingService != null)
				return sharingService.situationList.get(position)._id;
			else
				return Integer.MIN_VALUE;
		}

		@Override
		public int getCount()
		{
			if (sharingService != null)
				return sharingService.situationList.size();
			else
				return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View v;
			if (convertView == null)
			{
				v = mInflater.inflate(mItemLayout, parent, false);
			}
			else
			{
				v = convertView;
				v = mInflater.inflate(mItemLayout, parent, false);
			}
			Situation stn = getItem(position);
			if (stn != null && sharingService != null)
			{
				TextView text = (TextView) v.findViewById(R.id.name);
				if (text != null)
				{
					text.setText(stn.name);
				}
				double dist = Geo.distance(stn.latitude, stn.longitude, sharingService.currentLocation.getLatitude(), sharingService.currentLocation.getLongitude());
				String distance = StringFormatter.distanceH(dist);
				text = (TextView) v.findViewById(R.id.distance);
				if (text != null)
				{
					text.setText(distance);
				}
				String track = StringFormatter.bearingH(stn.track);
				text = (TextView) v.findViewById(R.id.track);
				if (text != null)
				{
					text.setText(track);
				}
				String speed = String.valueOf(Math.round(stn.speed * sharingService.speedFactor));
				text = (TextView) v.findViewById(R.id.speed);
				if (text != null)
				{
					text.setText(speed);
				}
				int d = (int) ((sharingService.currentLocation.getTime() - stn.time) / 1000);
				String delay = StringFormatter.timeHP(d, timeoutInterval);
				text = (TextView) v.findViewById(R.id.delay);
				if (text != null)
				{
					text.setText(delay);
				}
			}
			return v;
		}

		@Override
		public boolean hasStableIds()
		{
			return true;
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
	{
		if (getString(R.string.pref_sharing_timeout).equals(key))
		{
	        timeoutInterval = sharedPreferences.getInt(key, getResources().getInteger(R.integer.def_sharing_timeout)) * 60;
		}
		if (adapter != null)
			adapter.notifyDataSetChanged();
	}
}
