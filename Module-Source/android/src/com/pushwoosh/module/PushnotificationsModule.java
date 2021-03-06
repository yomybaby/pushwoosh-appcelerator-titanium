/**
 * Pushwoosh SDK
 * (c) Pushwoosh 2012
 *
 */
package com.pushwoosh.module;

import java.util.HashMap;
import java.util.Map;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollEventCallback;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;

import org.appcelerator.titanium.*;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.IntentProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiConfig;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import com.pushwoosh.BasePushMessageReceiver;
import com.pushwoosh.BaseRegistrationReceiver;
import com.pushwoosh.PushManager;

import android.os.Bundle;

//Class: PushnotificationsModule
//Class to interact with Pushwoosh Push Notifications module
//
//Example:
//(start code)
//var pushnotifications = require('com.pushwoosh.module');
//Ti.API.info("module is => " + pushnotifications);
//	
//pushnotifications.pushNotificationsRegister({
//  "pw_appid": "ENTER_PUSHWOOSH_APPID_HERE",
//  "gcm_projectid": "ENTER_GOOGLE_PROJECTID_HERE",
//	success:function(e)
//	{
//		Ti.API.info('JS registration success event: ' + e.registrationId);
//	},
//	error:function(e)
//	{
//		Ti.API.error("Error during registration: "+e.error);
//	},
//	callback:function(e) // called when a push notification is received
//	{
//		Ti.API.info('JS message event: ' + JSON.stringify(e.data));
//	}
//});
//(end)
@Kroll.module(name="Pushwoosh", id="com.pushwoosh.module")
public class PushnotificationsModule extends KrollModule
{

	// Standard Debugging variables
	private static final String LCAT = "PushnotificationsModule";
	private static final boolean DBG = TiConfig.LOGD;
	
	boolean broadcastPush = true;

	// You can define constants with @Kroll.constant, for example:
	// @Kroll.constant public static final String EXTERNAL_NAME = value;

	public static PushnotificationsModule INSTANCE = null;
	
	protected void finalize()
	{
		INSTANCE = null;
		Log.d(LCAT, "Push: finalized");
	}
	
	public PushnotificationsModule()
	{
		super();
		INSTANCE = this;
		Log.d(LCAT, "Push: create module");
		
		// lifecycle callbacks are available since android 14 API
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			TiApplication.getInstance().registerActivityLifecycleCallbacks(new ActivityMonitor());
		}

		try
		{
			Context context = TiApplication.getInstance();
			ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
			broadcastPush = ai.metaData.getBoolean("PW_BROADCAST_PUSH", true);
		}
		catch(Exception e)
		{
			// ignore
		}
	}
	
	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		Log.d(LCAT, "inside onAppCreate");
		// put module init code that needs to run when the application is created
	}
	
	@Override
	protected void initActivity(Activity activity) {
		Log.d(LCAT, "Push: init activity!");
		super.initActivity(activity);
	}

	@Override
	public void onDestroy(Activity activity) {
		super.onDestroy(activity);

		Log.d(LCAT, "Push: on destroy");
	}
	
	@Override
	public void onPause(Activity activity) {
		super.onPause(activity);
		
		Log.d(LCAT, "Push: on pause");
		return;
	}
 
	@Override
	public void onResume(Activity activity) {
		super.onResume(activity);
		
		Log.d(LCAT, "Push: on resume");
	}
	
	//Registration receiver
	BaseRegistrationReceiver mBroadcastReceiver = new BaseRegistrationReceiver()
	{
		@Override
		public void onRegisterActionReceive(Context context, Intent intent)
		{
			Log.d(LCAT, "Push: register broadcast received");

			checkMessage(intent);
		}
	};
	
	//Push message receiver
	private BasePushMessageReceiver mReceiver = new BasePushMessageReceiver()
	{
		@Override
		protected void onMessageReceive(Intent intent)
		{
			Log.d(LCAT, "Push: message received");

			//JSON_DATA_KEY contains JSON payload of push notification.
			sendMessage(intent.getExtras().getString(JSON_DATA_KEY));
		}
	};
	
	//Registration of the receivers
	public void registerReceivers()
	{
		//sometimes titanium alloy doesn't call onPause or onResume 
		unregisterReceivers();
		
		Log.d(LCAT, "Push: register receivers");

		IntentFilter intentFilter = new IntentFilter(TiApplication.getInstance().getRootActivity().getPackageName() + ".action.PUSH_MESSAGE_RECEIVE");

		if(broadcastPush)
			TiApplication.getInstance().getRootActivity().registerReceiver(mReceiver, intentFilter);
		
		TiApplication.getInstance().getRootActivity().registerReceiver(mBroadcastReceiver, new IntentFilter(TiApplication.getInstance().getRootActivity().getPackageName() + "." + PushManager.REGISTER_BROAD_CAST_ACTION));
		
		Log.d(LCAT, "Push: finished registering receivers");

	}
	
	public void unregisterReceivers()
	{
		Log.d(LCAT, "Push: unregistering receivers");

		//Unregister receivers on pause
		try
		{
			TiApplication.getInstance().getRootActivity().unregisterReceiver(mReceiver);
		}
		catch (Exception e)
		{
			// pass.
		}
		
		try
		{
			TiApplication.getInstance().getRootActivity().unregisterReceiver(mBroadcastReceiver);
		}
		catch (Exception e)
		{
			//pass through
		}
		
		Log.d(LCAT, "Push: finished unregistering receivers");

	}
	
	private KrollFunction successCallback = null;
	private KrollFunction errorCallback = null;
	private KrollFunction messageCallback = null;
	
	PushManager mPushManager = null;
	
//Function: pushNotificationsRegister
//Call this to register for push notifications and retreive a push token
//
//Example:
//(start code)
//pushnotifications.pushNotificationsRegister({
//  "pw_appid": "ENTER_PUSHWOOSH_APPID_HERE",
//  "gcm_projectid": "ENTER_GOOGLE_PROJECTID_HERE",
//	success:function(e)
//	{
//		Ti.API.info('JS registration success event: ' + e.registrationId);
//	},
//	error:function(e)
//	{
//		Ti.API.error("Error during registration: "+e.error);
//	},
//	callback:function(e) // called when a push notification is received
//	{
//		Ti.API.info('JS message event: ' + JSON.stringify(e.data));
//	}
//});
//(end)
	@Kroll.method
	public void pushNotificationsRegister(HashMap options)
	{
		Log.d(LCAT, "Push: registering for pushes");

		// On Andoid < 4.0 registration is handled by IntentReceiver class
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			registerReceivers();
		}

		String googleProjectId = (String)options.get("gcm_projectid");
		String pushwooshAppId = (String)options.get("pw_appid");

		successCallback = (KrollFunction)options.get("success");
		errorCallback = (KrollFunction)options.get("error");
		messageCallback = (KrollFunction)options.get("callback");

		checkMessage(TiApplication.getInstance().getRootActivity().getIntent());
		resetIntentValues(TiApplication.getInstance().getRootActivity());

		PushManager.initializePushManager(TiApplication.getInstance(), pushwooshAppId, googleProjectId);
		mPushManager = PushManager.getInstance(TiApplication.getInstance());

		try
		{
			mPushManager.onStartup(TiApplication.getInstance());
			mPushManager.registerForPushNotifications();
		}
		catch (Exception e)
		{
			e.printStackTrace();
			sendError("Failed to register for push notifications");
			return;
		}
		
		Log.d(LCAT, "Push: finished registering for pushes");

		return;
	}
	
//Function: unregister
//Unregisters device from push notifications
	@Kroll.method
	public void unregister() {
		Log.d(LCAT, "unregister called");
		if (mPushManager == null)
		{
			return;
		}
		mPushManager.unregisterForPushNotifications();	
	}
	
//Function: startTrackingGeoPushes
//Starts geolocation based push notifications. You need to configure Geozones in Pushwoosh Control panel.
	@Kroll.method
	public void startTrackingGeoPushes() {
		Log.d(LCAT, "start tracking geo pushes called");
		if (mPushManager == null)
		{
			return;
		}
		mPushManager.startTrackingGeoPushes();
	}
 
//Function: stopTrackingGeoPushes
//Stops geolocation based push notifications
	@Kroll.method
	public void stopTrackingGeoPushes() {
		Log.d(LCAT, "stop tracking geo pushes called");
		if (mPushManager == null)
		{
			return;
		}
		mPushManager.stopTrackingGeoPushes();
	}

//Function: setTags
//Call this to set tags for the device
//
//Example:
//sets the following tags: "deviceName" with value "hello" and "deviceId" with value 10
//(start code)
//	pushnotifications.setTags({deviceName:"hello", deviceId:10});
//
//	//setings list tags "MyTag" with values (array) "hello", "world"
//	pushnotifications.setTags({"MyTag":["hello", "world"]});
//(end)	
	@Kroll.method
	public void setTags(HashMap params)
	{
		if (mPushManager == null)
		{
			return;
		}

		try
		{
			PushManager.sendTags(TiApplication.getInstance(), params, null);
			return;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return;
		}
	}

//Function: scheduleLocalNotification
//Android only, Creates local notification.
//
//Example:
//(start code)
//pushnotification.scheduleLocalNotification("Your pumpkins are ready!", 30);
//(end)
	@Kroll.method
	public int scheduleLocalNotification(String message, int seconds)
	{
		return PushManager.scheduleLocalNotification(TiApplication.getInstance(), message, seconds);
	}

//Function: clearLocalNotification
//Android only, Clears pending local notification created by <scheduleLocalNotification>
	@Kroll.method
	public void clearLocalNotification(int id)
	{
		PushManager.clearLocalNotification(TiApplication.getInstance(), id);
	}
	
//Function: clearLocalNotifications
//Android only, Clears all pending local notifications created by <scheduleLocalNotification>
	@Kroll.method
	public void clearLocalNotifications()
	{
		PushManager.clearLocalNotifications(TiApplication.getInstance());
	}

//Function: setMultiNotificationMode
//Android only, Allows multiple notifications in notification bar.
	@Kroll.method
	public void setMultiNotificationMode()
	{
		PushManager.setMultiNotificationMode(TiApplication.getInstance());
	}

//Function: setSimpleNotificationMode
//Android only, Allows only the last notification in notification bar.
	@Kroll.method
	public void setSimpleNotificationMode()
	{
		PushManager.setSimpleNotificationMode(TiApplication.getInstance());
	}


//Function: setBadgeNumber
//Android only, Set application icon badge number
	@Kroll.method
	public void setBadgeNumber(int badgeNumber)
	{
		mPushManager.setBadgeNumber(badgeNumber);
	}

//Function: getBadgeNumber
//Android only, Get application icon badge number
	@Kroll.method
	public int getBadgeNumber()
	{
		return mPushManager.getBadgeNumber();
	}

//Function: addBadgeNumber
//Android only, Add to application icon badge number
	@Kroll.method
	public void addBadgeNumber(int deltaBadge)
	{
		mPushManager.addBadgeNumber(deltaBadge);
	}

	public void checkMessage(Intent intent)
	{
		if(intent == null)
		{
			Log.d(LCAT, "CHECK MESSAGE: intent null");
			return;
		}
		if (null != intent)
		{
			if (intent.hasExtra(PushManager.PUSH_RECEIVE_EVENT))
			{
				Log.d(LCAT, "CHECK MESSAGE: push receive");
				sendMessage(intent.getExtras().getString(PushManager.PUSH_RECEIVE_EVENT));
			}
			else if (intent.hasExtra(PushManager.REGISTER_EVENT))
			{
				Log.d(LCAT, "CHECK MESSAGE: push register");
				sendSuccess(intent.getExtras().getString(PushManager.REGISTER_EVENT));
			}
			else if (intent.hasExtra(PushManager.UNREGISTER_EVENT))
			{
				Log.d(LCAT, "CHECK MESSAGE: push unregister");
			}
			else if (intent.hasExtra(PushManager.REGISTER_ERROR_EVENT))
			{
				Log.d(LCAT, "CHECK MESSAGE: push error");
				sendError(intent.getExtras().getString(PushManager.REGISTER_ERROR_EVENT));
			}
			else if (intent.hasExtra(PushManager.UNREGISTER_ERROR_EVENT))
			{
				Log.d(LCAT, "CHECK MESSAGE: unregister error");
			}
		}
	}
	
	public void sendSuccess(final String registrationId) {
		if(successCallback == null)
			return;
		
		TiApplication.getInstance().getRootActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				HashMap data = new HashMap();
				data.put("registrationId", registrationId);

				successCallback.callAsync(getKrollObject(),data);
			}
		});
	}

	public void sendError(final String error) {
		if(errorCallback == null)
			return;
		
		TiApplication.getInstance().getRootActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				HashMap data = new HashMap();
				data.put("error", error);

				errorCallback.callAsync(getKrollObject(),data);
			}
		});
	}

	public void sendMessage(final String messageData) {
		if(messageCallback == null)
			return;

		TiApplication.getInstance().getRootActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				HashMap data = new HashMap();
				data.put("data", messageData);

				messageCallback.call(getKrollObject(),data);
			}
		});
	}
	
	public void resetIntentValues(Activity activity)
	{
		if(activity == null)
			return;
			
		Intent mainAppIntent = activity.getIntent();

		if (mainAppIntent.hasExtra(PushManager.PUSH_RECEIVE_EVENT))
		{
			mainAppIntent.removeExtra(PushManager.PUSH_RECEIVE_EVENT);
		}
		else if (mainAppIntent.hasExtra(PushManager.REGISTER_EVENT))
		{
			mainAppIntent.removeExtra(PushManager.REGISTER_EVENT);
		}
		else if (mainAppIntent.hasExtra(PushManager.UNREGISTER_EVENT))
		{
			mainAppIntent.removeExtra(PushManager.UNREGISTER_EVENT);
		}
		else if (mainAppIntent.hasExtra(PushManager.REGISTER_ERROR_EVENT))
		{
			mainAppIntent.removeExtra(PushManager.REGISTER_ERROR_EVENT);
		}
		else if (mainAppIntent.hasExtra(PushManager.UNREGISTER_ERROR_EVENT))
		{
			mainAppIntent.removeExtra(PushManager.UNREGISTER_ERROR_EVENT);
		}

		activity.setIntent(mainAppIntent);
	}
}

