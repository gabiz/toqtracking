/*******************************************************************************
 * Copyright (c) 2014 Qualcomm Connected Experiences, Inc, All rights reserved
 ******************************************************************************/
package com.qualcomm.toq.smartwatch.api.v1.deckofcards.demo;

import java.io.InputStream;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.qualcomm.toq.smartwatch.api.v1.deckofcards.Constants;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.ListCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.NotificationTextCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.card.SimpleTextCard;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManager;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManagerListener;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteDeckOfCards;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteDeckOfCardsException;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteResourceStore;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.RemoteToqNotification;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.resource.DeckOfCardsLauncherIcon;
import com.qualcomm.toq.smartwatch.api.v1.deckofcards.util.ParcelableUtil;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;


/**
 * Demo client which uses the Toq api library, which is part of the Toq SDK, to communicate 
 * with the Toq app's API service to install/update/uninstall a deck of cards applet on a 
 * Toq watch and send notifications to the Toq watch.
 * 
 * To run the demo (assuming you have already installed the Toq app and paired it with the 
 * Toq watch), just install this app to the same device as the Toq app and launch it.
 * 
 * @author mcaunter
 */
public class ToqApiDemo extends Activity{


    private final static String DEMO_PREFS_FILE= "demo_prefs_file";
    private final static String DECK_OF_CARDS_KEY= "deck_of_cards_key";
    private final static String DECK_OF_CARDS_VERSION_KEY= "deck_of_cards_version_key";
    
    private DeckOfCardsManager deckOfCardsManager;
        
    private DeckOfCardsManagerListener deckOfCardsManagerListener;
    private DeckOfCardsEventListener deckOfCardsEventListener;
    
    private ToqAppStateBroadcastReceiver toqAppStateReceiver;
    
    private RemoteResourceStore resourceStore;   

    private RemoteDeckOfCards deckOfCards;
        
    private ViewGroup deckOfCardsPanel;
    private Button installDeckOfCardsButton;
    private Button updateDeckOfCardsButton;
    private Button uninstallDeckOfCardsButton;
    
    private ViewGroup notificationPanel;
    private Button sendNotificationButton;

    private TextView statusTextView;
    
    /*
     * Lifecycle methods
     */
    

    /**
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    public void onCreate(Bundle icicle){
                
        super.onCreate(icicle);
        
        Log.d(Constants.TAG, "ToqApiDemo.onCreate");

        setContentView(R.layout.main);
        
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        
        // Get the reference to the deck of cards manager
        deckOfCardsManager= DeckOfCardsManager.getInstance(getApplicationContext());
//        Logger.setLoggingEnabled(false); // Disable api logging
//        Logger.setTag("MyApp"); // Set custom api logging tag

        // Create listeners
        deckOfCardsManagerListener= new DeckOfCardsManagerListenerImpl();
        deckOfCardsEventListener= new DeckOfCardsEventListenerImpl();
        
        // Create the state receiver
        toqAppStateReceiver= new ToqAppStateBroadcastReceiver();
        
        // Init
        initDeckOfCards();       
        initUI();
    }
    
    
    /**
     * @see android.app.Activity#onStart()
     */
    protected void onStart(){

        super.onStart();
        
		Log.d(Constants.TAG, "ToqApiDemo.onStart");        
        
        // Add the listeners
        deckOfCardsManager.addDeckOfCardsManagerListener(deckOfCardsManagerListener);
        deckOfCardsManager.addDeckOfCardsEventListener(deckOfCardsEventListener);
        
        // Register toq app state receiver
        registerToqAppStateReceiver();

        // If not connected, try to connect
        if (!deckOfCardsManager.isConnected()){
            
            setStatus(getString(R.string.status_connecting));
            
            Log.d(Constants.TAG, "ToqApiDemo.onStart - not connected, connecting...");   

            try{
                deckOfCardsManager.connect();
            }
            catch (RemoteDeckOfCardsException e){
                Toast.makeText(this, getString(R.string.error_connecting_to_service), Toast.LENGTH_SHORT).show();
                Log.e(Constants.TAG, "ToqApiDemo.onStart - error connecting to Toq app service", e);
            }
            
        }
        else{
            Log.d(Constants.TAG, "ToqApiDemo.onStart - already connected");
            setStatus(getString(R.string.status_connected));
            refreshUI();
        }

    }    
    

    /**
     * @see android.app.Activity#onStop()
     */
    public void onStop(){
        
        super.onStop();

        Log.d(Constants.TAG, "ToqApiDemo.onStop");

        // Unregister toq app state receiver
        unregisterStateReceiver();
        
        // Remove listeners
        deckOfCardsManager.removeDeckOfCardsManagerListener(deckOfCardsManagerListener);
        deckOfCardsManager.removeDeckOfCardsEventListener(deckOfCardsEventListener);
    }
    
    
    /**
     * @see android.app.Activity#onDestroy()
     */
    public void onDestroy(){
        
        super.onDestroy();
        
        Log.d(Constants.TAG, "ToqApiDemo.onDestroy");

        deckOfCardsManager.disconnect();
    }
    
    
    /*
     * Private classes
     */
    
    
    // Handle service connection lifecycle and installation events
    private class DeckOfCardsManagerListenerImpl implements DeckOfCardsManagerListener{

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManagerListener#onConnected()
         */
        public void onConnected(){            
            runOnUiThread(new Runnable(){
                public void run(){                   
                    setStatus(getString(R.string.status_connected));
                    refreshUI();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManagerListener#onDisconnected()
         */
        public void onDisconnected(){
            runOnUiThread(new Runnable(){
                public void run(){                    
                    setStatus(getString(R.string.status_disconnected));
                    disableUI();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManagerListener#onInstallationSuccessful()
         */
        public void onInstallationSuccessful(){            
            runOnUiThread(new Runnable(){
                public void run(){                   
                    setStatus(getString(R.string.status_installation_successful));
                    updateUIInstalled();
                }
            });           
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManagerListener#onInstallationDenied()
         */
        public void onInstallationDenied(){
            runOnUiThread(new Runnable(){
                public void run(){                    
                    setStatus(getString(R.string.status_installation_denied));
                    updateUINotInstalled();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.remote.DeckOfCardsManagerListener#onUninstalled()
         */
        public void onUninstalled(){
            runOnUiThread(new Runnable(){
                public void run(){ 
                    setStatus(getString(R.string.status_uninstalled));                    
                    updateUINotInstalled();
                }
            });
        }
        
    }
    
    
    // Handle card events triggered by the user interacting with a card in the installed deck of cards
    private class DeckOfCardsEventListenerImpl implements DeckOfCardsEventListener{

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onCardOpen(java.lang.String)
         */
        public void onCardOpen(final String cardId){
            runOnUiThread(new Runnable(){
                public void run(){
                    Toast.makeText(ToqApiDemo.this, getString(R.string.event_card_open) + cardId, Toast.LENGTH_SHORT).show();               
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onCardVisible(java.lang.String)
         */
        public void onCardVisible(final String cardId){
            runOnUiThread(new Runnable(){
                public void run(){
                    Toast.makeText(ToqApiDemo.this, getString(R.string.event_card_visible) + cardId, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onCardInvisible(java.lang.String)
         */
        public void onCardInvisible(final String cardId){
            runOnUiThread(new Runnable(){
                public void run(){
                    Toast.makeText(ToqApiDemo.this, getString(R.string.event_card_invisible) + cardId, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onCardClosed(java.lang.String)
         */
        public void onCardClosed(final String cardId){
            runOnUiThread(new Runnable(){
                public void run(){
                    Toast.makeText(ToqApiDemo.this, getString(R.string.event_card_closed) + cardId, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * @see com.qualcomm.toq.smartwatch.api.v1.deckofcards.DeckOfCardsEventListener#onMenuOptionSelected(java.lang.String, java.lang.String)
         */
        public void onMenuOptionSelected(final String cardId, final String menuOption){
            runOnUiThread(new Runnable(){
                public void run(){
                    Toast.makeText(ToqApiDemo.this, getString(R.string.event_menu_option_selected) + cardId + " [" + menuOption +"]", Toast.LENGTH_SHORT).show();
                }
            });
        }

    }
    
    
    // Toq app state receiver
    private class ToqAppStateBroadcastReceiver extends BroadcastReceiver{

        /**
         * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
         */
        public void onReceive(Context context, Intent intent){

            String action= intent.getAction();

            if (action == null){
                Log.w(Constants.TAG, "ToqApiDemo.ToqAppStateBroadcastReceiver.onReceive - action is null, returning");
                return;
            }
            
            Log.d(Constants.TAG, "ToqApiDemo.ToqAppStateBroadcastReceiver.onReceive - action: " + action);            
            
            // If watch is now connected, refresh UI
            if (action.equals(Constants.TOQ_WATCH_CONNECTED_INTENT)){ 
                Toast.makeText(ToqApiDemo.this, getString(R.string.intent_toq_watch_connected), Toast.LENGTH_SHORT).show();
                refreshUI();               
            }
            // Else if watch is now disconnected, disable UI
            else if (action.equals(Constants.TOQ_WATCH_DISCONNECTED_INTENT)){ 
                Toast.makeText(ToqApiDemo.this, getString(R.string.intent_toq_watch_disconnected), Toast.LENGTH_SHORT).show();
                disableUI();
            }
            
        }

    }
    
    
    /*
     * Private API
     */
    

    // Connected to Toq app service, so refresh the UI
    private void refreshUI(){

        try{
            
            // If Toq watch is connected
            if (deckOfCardsManager.isToqWatchConnected()){

                // If the deck of cards applet is already installed
                if (deckOfCardsManager.isInstalled()){
                    Log.d(Constants.TAG, "ToqApiDemo.refreshUI - already installed");
                    updateUIInstalled();
                }
                // Else not installed
                else{
                    Log.d(Constants.TAG, "ToqApiDemo.refreshUI - not installed"); 
                    updateUINotInstalled();
                }

            }
            // Else not connected to the Toq app
            else{
                Log.d(Constants.TAG, "ToqApiDemo.refreshUI - Toq watch is disconnected");
                Toast.makeText(ToqApiDemo.this, getString(R.string.intent_toq_watch_disconnected), Toast.LENGTH_SHORT).show();
                disableUI();
            }

        }
        catch (RemoteDeckOfCardsException e){
            Toast.makeText(this, getString(R.string.error_checking_status), Toast.LENGTH_SHORT).show();
            Log.e(Constants.TAG, "ToqApiDemo.refreshUI - error checking if Toq watch is connected or deck of cards is installed", e);
        }
        
    }
    
    
    // Disable all UI components
    private void disableUI(){       
        // Disable everything
        setChildrenEnabled(deckOfCardsPanel, false); 
        setChildrenEnabled(notificationPanel, false);
    }
    
    
    // Set up UI for when deck of cards applet is already installed
    private void updateUIInstalled(){
        
        // Enable everything
        setChildrenEnabled(deckOfCardsPanel, true);
        setChildrenEnabled(notificationPanel, true);
        
        // Install disabled; update, uninstall enabled
        installDeckOfCardsButton.setEnabled(false);
        updateDeckOfCardsButton.setEnabled(true);
        uninstallDeckOfCardsButton.setEnabled(true); 
        
        // Focus
        updateDeckOfCardsButton.requestFocus();
    }
    
    
    // Set up UI for when deck of cards applet is not installed
    private void updateUINotInstalled(){
        
        // Disable notification panel
        setChildrenEnabled(notificationPanel, false);

        // Enable deck of cards panel
        setChildrenEnabled(deckOfCardsPanel, true);
        
        // Install enabled; update, uninstall disabled
        installDeckOfCardsButton.setEnabled(true);
        updateDeckOfCardsButton.setEnabled(false);
        uninstallDeckOfCardsButton.setEnabled(false);
        
        // Focus
        installDeckOfCardsButton.requestFocus();
    }
    
    
    // Register state receiver
    private void registerToqAppStateReceiver(){
        IntentFilter intentFilter= new IntentFilter();
        intentFilter.addAction(Constants.BLUETOOTH_ENABLED_INTENT);
        intentFilter.addAction(Constants.BLUETOOTH_DISABLED_INTENT);
        intentFilter.addAction(Constants.TOQ_WATCH_PAIRED_INTENT);
        intentFilter.addAction(Constants.TOQ_WATCH_UNPAIRED_INTENT);
        intentFilter.addAction(Constants.TOQ_WATCH_CONNECTED_INTENT);
        intentFilter.addAction(Constants.TOQ_WATCH_DISCONNECTED_INTENT);
        getApplicationContext().registerReceiver(toqAppStateReceiver, intentFilter);
    }
    
    
    // Unregister state receiver 
    private void unregisterStateReceiver(){
        getApplicationContext().unregisterReceiver(toqAppStateReceiver);
    }
    
    
    // Set status bar message
    private void setStatus(String msg){
        statusTextView.setText(msg);
    }
    
    
    // Initialise deck of cards
    private void initDeckOfCards(){
           
        // Try to retrieve a stored deck of cards
        try{
            
            // If there is no stored deck of cards or it is unusable, then create new and store
            if ((deckOfCards= getStoredDeckOfCards()) == null){               
                deckOfCards= createDeckOfCards();
                storeDeckOfCards();                
            }
            
        }
        catch (Throwable th){
            Log.w(Constants.TAG, "ToqApiDemo.initDeckOfCards - error occurred retrieving the stored deck of cards: " + th.getMessage());
            deckOfCards= null; // Reset to force recreate
        }
        
        // Make sure in usable state
        if (deckOfCards == null){
            deckOfCards= createDeckOfCards();
        }
        
        // Get the icons
        resourceStore= new RemoteResourceStore();

        try{            
            
            DeckOfCardsLauncherIcon whiteIcon= new DeckOfCardsLauncherIcon("white", getIcon("white.png"), DeckOfCardsLauncherIcon.WHITE);
            DeckOfCardsLauncherIcon colorIcon= new DeckOfCardsLauncherIcon("color", getIcon("color.png"), DeckOfCardsLauncherIcon.COLOR);

            // Re-add the icons
            deckOfCards.setLauncherIcons(resourceStore, new DeckOfCardsLauncherIcon[]{whiteIcon, colorIcon});

        }
        catch (Exception e){
            Toast.makeText(this, getString(R.string.error_initialising_deck_of_cards), Toast.LENGTH_SHORT).show();
            Log.e(Constants.TAG, "ToqApiDemo.initDeckOfCards - error occurred parsing the icons", e);
        }
        
    }

    
    // Get stored deck of cards if one exists
    private RemoteDeckOfCards getStoredDeckOfCards() throws Exception{
        
        if (!isValidDeckOfCards()){
            Log.w(Constants.TAG, "ToqApiDemo.getStoredDeckOfCards - stored deck of cards not valid for this version of the demo, recreating...");
            return null;
        }
        
        SharedPreferences prefs= getSharedPreferences(DEMO_PREFS_FILE, Context.MODE_PRIVATE);
        String deckOfCardsStr= prefs.getString(DECK_OF_CARDS_KEY, null);
        
        if (deckOfCardsStr == null){
            return null;
        }
        else{
            return ParcelableUtil.unmarshall(deckOfCardsStr, RemoteDeckOfCards.CREATOR);
        }
        
    }
    
    // Store deck of cards
    private void storeDeckOfCards() throws Exception{        
        SharedPreferences prefs= getSharedPreferences(DEMO_PREFS_FILE, Context.MODE_PRIVATE);
        Editor editor= prefs.edit();
        editor.putString(DECK_OF_CARDS_KEY, ParcelableUtil.marshall(deckOfCards));
        editor.putInt(DECK_OF_CARDS_VERSION_KEY, Constants.VERSION_CODE);
        editor.commit();       
    } 
    
    
    // Check if the stored deck of cards is valid for this version of the demo
    private boolean isValidDeckOfCards(){
        
        SharedPreferences prefs= getSharedPreferences(DEMO_PREFS_FILE, Context.MODE_PRIVATE);
        int deckOfCardsVersion= prefs.getInt(DECK_OF_CARDS_VERSION_KEY, 0);

        if (deckOfCardsVersion < Constants.VERSION_CODE){
            return false;
        }
        
        return true;
    }
    
    public String readJson(String url) {
        StringBuilder builder = new StringBuilder();
        HttpClient client = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(url);
        try {
          HttpResponse response = client.execute(httpGet);
          StatusLine statusLine = response.getStatusLine();
          int statusCode = statusLine.getStatusCode();
          if (statusCode == 200) {
            HttpEntity entity = response.getEntity();
            InputStream content = entity.getContent();
            BufferedReader reader = new BufferedReader(new InputStreamReader(content));
            String line;
            while ((line = reader.readLine()) != null) {
              builder.append(line);
            }
          } else {
            Log.e(ToqApiDemo.class.toString(), "Failed to download file");
          }
        } catch (ClientProtocolException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
        return builder.toString();
      }
    
    
    // Create some cards with example content
    private RemoteDeckOfCards createDeckOfCards(){
        
    	
    	ListCard listCard= new ListCard();

        
        // Card 1
        SimpleTextCard simpleTextCard= new SimpleTextCard("card1", "Header 1", System.currentTimeMillis(), "Title 1", new String[]{"Card 1: line 1", "Card 1: line 2", "Card 1: line 3"});
        simpleTextCard.setInfoText("10");
        simpleTextCard.setReceivingEvents(true);
        simpleTextCard.setMenuOptions(new String[]{"aaa", "bbb", "ccc"});
        simpleTextCard.setShowDivider(true);
        listCard.add(simpleTextCard);
        
        // Card 2
        simpleTextCard= new SimpleTextCard("card2", "Header 2", System.currentTimeMillis(), "Title 2", new String[]{"Card 2: line 1", "Card 2: line 2", "Card 2: line 3"});
        simpleTextCard.setInfoText("20");
        simpleTextCard.setReceivingEvents(true);
        simpleTextCard.setMenuOptions(new String[]{"111", "222", "333"});
        simpleTextCard.setShowDivider(false);
        listCard.add(simpleTextCard);

        // Card 3
        simpleTextCard= new SimpleTextCard("card3", "Header 3", System.currentTimeMillis(), "Title 3", new String[]{"Card 3: line 1", "Card 3: line 2", "Card 3: line 3"});
        simpleTextCard.setInfoText("30");
        simpleTextCard.setReceivingEvents(false);
        simpleTextCard.setMenuOptions(new String[]{"xxx", "yyy", "zzz"});
        simpleTextCard.setShowDivider(true);
        listCard.add(simpleTextCard);
        
        // Card 3
        simpleTextCard= new SimpleTextCard("card4", "Header 4", System.currentTimeMillis(), "Title 3", new String[]{"Card 3: line 1", "Card 3: line 2", "Card 3: line 3"});
        simpleTextCard.setInfoText("30");
        simpleTextCard.setReceivingEvents(false);
        simpleTextCard.setMenuOptions(new String[]{"xxx", "yyy", "zzz"});
        simpleTextCard.setShowDivider(true);
        listCard.add(simpleTextCard);

        return new RemoteDeckOfCards(this, listCard);  
    }

    
    // Initialise the UI
    private void initUI(){
        
        // Panels
        notificationPanel= (ViewGroup)findViewById(R.id.notification_panel);
        deckOfCardsPanel= (ViewGroup)findViewById(R.id.doc_panel);
        
        setChildrenEnabled(deckOfCardsPanel, false);
        setChildrenEnabled(notificationPanel, false);

        // Buttons
        installDeckOfCardsButton= (Button)findViewById(R.id.doc_install_button);
        installDeckOfCardsButton.setOnClickListener(new OnClickListener(){
            public void onClick(View v){            
                installDeckOfCards();
            }
        });
        
        updateDeckOfCardsButton= (Button)findViewById(R.id.doc_update_button);
        updateDeckOfCardsButton.setOnClickListener(new OnClickListener(){
            public void onClick(View v){            
                updateDeckOfCards();
            }
        });
        
        uninstallDeckOfCardsButton= (Button)findViewById(R.id.doc_uninstall_button);
        uninstallDeckOfCardsButton.setOnClickListener(new OnClickListener(){
            public void onClick(View v){            
                uninstallDeckOfCards();
            }
        });
        
        sendNotificationButton= (Button)findViewById(R.id.send_notification_button);
        sendNotificationButton.setOnClickListener(new OnClickListener(){
            public void onClick(View v){            
                sendNotification();
            }
        });
        
        // Deck of cards
        ListCard listCard= deckOfCards.getListCard();
        
        SimpleTextCard simpleTextCard= (SimpleTextCard)listCard.childAtIndex(0);        
        ((EditText)findViewById(R.id.doc1_header_text)).setText(simpleTextCard.getHeaderText());
        ((EditText)findViewById(R.id.doc1_title_text)).setText(simpleTextCard.getTitleText());
        ((EditText)findViewById(R.id.doc1_message_text)).setText(concatStrings(simpleTextCard.getMessageText()));
        ((EditText)findViewById(R.id.doc1_info_text)).setText(simpleTextCard.getInfoText());
        ((CheckBox)findViewById(R.id.doc1_events_checkbox)).setChecked(simpleTextCard.isReceivingEvents());
        ((EditText)findViewById(R.id.doc1_menu_options_text)).setText(concatStrings(simpleTextCard.getMenuOptions()));
        ((CheckBox)findViewById(R.id.doc1_divider_checkbox)).setChecked(simpleTextCard.isShowDivider());

        simpleTextCard= (SimpleTextCard)listCard.childAtIndex(1);        
        ((EditText)findViewById(R.id.doc2_header_text)).setText(simpleTextCard.getHeaderText());
        ((EditText)findViewById(R.id.doc2_title_text)).setText(simpleTextCard.getTitleText());
        ((EditText)findViewById(R.id.doc2_message_text)).setText(concatStrings(simpleTextCard.getMessageText()));
        ((EditText)findViewById(R.id.doc2_info_text)).setText(simpleTextCard.getInfoText());
        ((CheckBox)findViewById(R.id.doc2_events_checkbox)).setChecked(simpleTextCard.isReceivingEvents());
        ((EditText)findViewById(R.id.doc2_menu_options_text)).setText(concatStrings(simpleTextCard.getMenuOptions()));
        ((CheckBox)findViewById(R.id.doc2_divider_checkbox)).setChecked(simpleTextCard.isShowDivider());

        simpleTextCard= (SimpleTextCard)listCard.childAtIndex(2);        
        ((EditText)findViewById(R.id.doc3_header_text)).setText(simpleTextCard.getHeaderText());
        ((EditText)findViewById(R.id.doc3_title_text)).setText(simpleTextCard.getTitleText());
        ((EditText)findViewById(R.id.doc3_message_text)).setText(concatStrings(simpleTextCard.getMessageText()));
        ((EditText)findViewById(R.id.doc3_info_text)).setText(simpleTextCard.getInfoText());
        ((CheckBox)findViewById(R.id.doc3_events_checkbox)).setChecked(simpleTextCard.isReceivingEvents());
        ((EditText)findViewById(R.id.doc3_menu_options_text)).setText(concatStrings(simpleTextCard.getMenuOptions()));
        ((CheckBox)findViewById(R.id.doc3_divider_checkbox)).setChecked(simpleTextCard.isShowDivider());

        // Notification
        ((EditText)findViewById(R.id.notification_title_text)).setText("Title");
        ((EditText)findViewById(R.id.notification_message_text)).setText(concatStrings(new String[]{"Line 1", "Line 2", "Line 3"}));
        ((EditText)findViewById(R.id.notification_info_text)).setText("99");
        ((CheckBox)findViewById(R.id.notification_events_checkbox)).setChecked(true);
        ((EditText)findViewById(R.id.notification_menu_options_text)).setText(concatStrings(new String[]{"opt1", "opt2", "opt3"}));
        ((CheckBox)findViewById(R.id.notification_divider_checkbox)).setChecked(true);
        ((CheckBox)findViewById(R.id.notification_vibe_checkbox)).setChecked(true);

        // Status
        statusTextView= (TextView)findViewById(R.id.status_text);
        statusTextView.setText("Initialised");        
    }
    
    
    // Install deck of cards applet
    private void installDeckOfCards(){
        
        Log.d(Constants.TAG, "ToqApiDemo.installDeckOfCards");
        
        updateDeckOfCardsFromUI();
        
        try{                      
            deckOfCardsManager.installDeckOfCards(deckOfCards, resourceStore);
        }
        catch (RemoteDeckOfCardsException e){
            Toast.makeText(this, getString(R.string.error_installing_deck_of_cards), Toast.LENGTH_SHORT).show();
            Log.e(Constants.TAG, "ToqApiDemo.installDeckOfCards - error installing deck of cards applet", e);
        }
        
        try{
            storeDeckOfCards();
        }
        catch (Exception e){
            Log.e(Constants.TAG, "ToqApiDemo.installDeckOfCards - error storing deck of cards applet", e);
        }

    }
    
    
    // Update deck of cards applet
    private void updateDeckOfCards(){
        
        Log.d(Constants.TAG, "ToqApiDemo.updateDeckOfCards");
        
        updateDeckOfCardsFromUI();
        
        try{            
            deckOfCardsManager.updateDeckOfCards(deckOfCards, resourceStore);
        }
        catch (RemoteDeckOfCardsException e){
            Toast.makeText(this, getString(R.string.error_updating_deck_of_cards), Toast.LENGTH_SHORT).show();
            Log.e(Constants.TAG, "ToqApiDemo.updateDeckOfCards - error updating deck of cards applet", e);
        }
        
        try{
            storeDeckOfCards();
        }
        catch (Exception e){
            Log.e(Constants.TAG, "ToqApiDemo.updateDeckOfCards - error storing deck of cards applet", e);
        }

    }
    
    
    // Uninstall deck of cards applet
    private void uninstallDeckOfCards(){
        
        Log.d(Constants.TAG, "ToqApiDemo.uninstallDeckOfCards");
        
        try{                        
            deckOfCardsManager.uninstallDeckOfCards();
        }
        catch (RemoteDeckOfCardsException e){
            Toast.makeText(this, getString(R.string.error_uninstalling_deck_of_cards), Toast.LENGTH_SHORT).show();
            Log.e(Constants.TAG, "ToqApiDemo.uninstallDeckOfCards - error uninstalling deck of cards applet applet", e);
        }

    }
    
    
    // Send notification button
    private void sendNotification(){

        Log.d(Constants.TAG, "ToqApiDemo.sendNotification");
        
    	String readAutomaticFeed = readJson("https://trackingprozz.herokuapp.com/logs/last");
    	JSONObject jsonObj;
        try {
        	jsonObj = new JSONObject(readAutomaticFeed);
//            Log.i(ToqApiDemo.class.getName(),
//                "Number of entries " + jsonArray.length());
//            for (int i = 0; i < jsonArray.length(); i++) {
//              JSONObject jsonObject = jsonArray.getJSONObject(i);
//              Log.i(ToqApiDemo.class.getName(), jsonObject.getString("text"));
//            }
        NotificationTextCard notificationCard = null;

        String type = jsonObj.getString("type");
        
        if (type.equals("notification:hard_accel")) {
            String vehicle = jsonObj.getJSONObject("vehicle").getString("display_name");
            notificationCard = new NotificationTextCard(System.currentTimeMillis(), 
                    "A: Hard Acceleration", 
                    splitString("Car: "+vehicle)); 
        } else if (type.equals("notification:hard_accel")) {
            String vehicle = jsonObj.getJSONObject("vehicle").getString("display_name");
            notificationCard = new NotificationTextCard(System.currentTimeMillis(), 
                    "A: Hard Acceleration", 
                    splitString("Car: "+vehicle)); 
        } else if (type.equals("notification:speeding")) {
        	
            String vehicle = jsonObj.getJSONObject("vehicle").getString("display_name");
            Long speed = jsonObj.getLong("speed_mph");

        	notificationCard = new NotificationTextCard(System.currentTimeMillis(), 
                    "A: Speeding",
                    splitString("Car: "+vehicle+"\nSpeed: "+speed+" mph")); 
        } else if (type.equals("notification:hard_brake")) {
            String vehicle = jsonObj.getJSONObject("vehicle").getString("display_name");
            notificationCard = new NotificationTextCard(System.currentTimeMillis(), 
                    "A: Hard Brake", 
                    splitString("Car: "+vehicle)); 
        } else if (type.equals("trip:finished")) {
            String vehicle = jsonObj.getJSONObject("vehicle").getString("display_name");
            String startName = jsonObj.getJSONObject("trip").getJSONObject("start_location").getString("name");
            String endName = jsonObj.getJSONObject("trip").getJSONObject("end_location").getString("name");
//            long time = jsonObj.getJSONObject("trip").getLong("start_time");
//            Double distance = jsonObj.getJSONObject("trip").getDouble("distance_m");

            notificationCard = new NotificationTextCard(System.currentTimeMillis(), 
                    "A: Trip Finished", 
                    splitString("Car: "+vehicle+"\nFrom: "+startName+"\nTo: "+endName)); 
            
        	
        } else if (type.equals("mil:on")) {
            String vehicle = jsonObj.getJSONObject("vehicle").getString("display_name");
            notificationCard = new NotificationTextCard(System.currentTimeMillis(), 
                    "A: Engine Failure", 
                    splitString("Car: "+vehicle+"\nSmall fuel vapor leak in EVAP system")); 
        }

//        notificationCard.setInfoText("abcd");
        notificationCard.setVibeAlert(true);
        notificationCard.setShowDivider(true);
        notificationCard.setReceivingEvents(false);
//        notificationCard.setMenuOptions(splitString(((EditText)findViewById(R.id.notification_menu_options_text)).getText().toString()));
        RemoteToqNotification notification= new RemoteToqNotification(this, notificationCard);

        try{            
            deckOfCardsManager.sendNotification(notification);
        }
        catch (RemoteDeckOfCardsException e){
            Toast.makeText(this, getString(R.string.error_sending_notification), Toast.LENGTH_SHORT).show();
            Log.e(Constants.TAG, "ToqApiDemo.sendNotification - error sending notification", e);
        }
        
        } catch (Exception e) {
            e.printStackTrace();
          }

    }

    
    // Enable/Disable a view group's children and nested children
    private void setChildrenEnabled(ViewGroup viewGroup, boolean isEnabled){

        for (int i = 0; i < viewGroup.getChildCount();  i++){

            View view= viewGroup.getChildAt(i);

            if (view instanceof ViewGroup){
                setChildrenEnabled((ViewGroup)view, isEnabled);
            }
            else{
                view.setEnabled(isEnabled);
            }

        }       
        
    }
  
  
    // Read an icon from assets and return as a bitmap
    private Bitmap getIcon(String fileName) throws Exception{

        try{
            InputStream is= getAssets().open(fileName);
            return BitmapFactory.decodeStream(is);
        }
        catch (Exception e){
            throw new Exception("An error occurred getting the icon: " + fileName, e);
        }

    }
    
    
    // Parse the UI to update the deck of cards contents
    private void updateDeckOfCardsFromUI(){

        ListCard listCard= deckOfCards.getListCard();
        
    	String readAutomaticFeed = readJson("https://trackingprozz.herokuapp.com/logs/reverse");
    	String readMood = readJson("https://trackingprozz.herokuapp.com/emotiv");
    	JSONArray jsonArray;
        try {
            jsonArray = new JSONArray(readAutomaticFeed);
            Log.i(ToqApiDemo.class.getName(),
                "Number of entries " + jsonArray.length());
//            for (int i = 0; i < jsonArray.length(); i++) {
//              JSONObject jsonObject = jsonArray.getJSONObject(i);
//              Log.i(ToqApiDemo.class.getName(), jsonObject.getString("text"));
//            }

       
        JSONObject obj1 = jsonArray.getJSONObject(0);
        String startName = obj1.getJSONObject("trip").getJSONObject("start_location").getString("name");
        String endName = obj1.getJSONObject("trip").getJSONObject("end_location").getString("name");
        long time = obj1.getJSONObject("trip").getLong("start_time");
        Double distance = obj1.getJSONObject("trip").getDouble("distance_m");
        // Card 1
        SimpleTextCard simpleTextCard= (SimpleTextCard)listCard.childAtIndex(0);        
        simpleTextCard.setHeaderText("Trip 1");
        simpleTextCard.setTitleText("Dist: ".concat(distance.toString()) );
        simpleTextCard.setMessageText(new String[]{startName, endName});
        simpleTextCard.setInfoText(((EditText)findViewById(R.id.doc1_info_text)).getText().toString());
        simpleTextCard.setReceivingEvents(((CheckBox)findViewById(R.id.doc1_events_checkbox)).isChecked());
        simpleTextCard.setShowDivider(((CheckBox)findViewById(R.id.doc1_divider_checkbox)).isChecked());
        simpleTextCard.setTimeMillis(time);
        
        if (((EditText)findViewById(R.id.doc1_menu_options_text)).getText().length() == 0){
            simpleTextCard.setMenuOptions(null); // If all menu options deleted, reset
        }
        else{
            simpleTextCard.setMenuOptions(splitString(((EditText)findViewById(R.id.doc1_menu_options_text)).getText().toString()));
        }     
              
        JSONObject obj2 = jsonArray.getJSONObject(1);
        String startName2 = obj2.getJSONObject("trip").getJSONObject("start_location").getString("name");
        String endName2 = obj2.getJSONObject("trip").getJSONObject("end_location").getString("name");
        long time2 = obj2.getJSONObject("trip").getLong("start_time");
        Double distance2 = obj2.getJSONObject("trip").getDouble("distance_m");
        // Card 2
        simpleTextCard= (SimpleTextCard)listCard.childAtIndex(1);        
        simpleTextCard.setHeaderText("Trip 2");
        simpleTextCard.setTitleText("Dist: ".concat(distance2.toString()));
        simpleTextCard.setMessageText(new String[]{startName2, endName2});
        simpleTextCard.setInfoText(((EditText)findViewById(R.id.doc2_info_text)).getText().toString());
        simpleTextCard.setReceivingEvents(((CheckBox)findViewById(R.id.doc2_events_checkbox)).isChecked());
        simpleTextCard.setShowDivider(((CheckBox)findViewById(R.id.doc2_divider_checkbox)).isChecked());
        simpleTextCard.setTimeMillis(time2);
        
        if (((EditText)findViewById(R.id.doc2_menu_options_text)).getText().length() == 0){
            simpleTextCard.setMenuOptions(null); // If all menu options deleted, reset
        }
        else{
            simpleTextCard.setMenuOptions(splitString(((EditText)findViewById(R.id.doc2_menu_options_text)).getText().toString()));
        }
        
        JSONObject obj3 = jsonArray.getJSONObject(2);
        String startName3 = obj3.getJSONObject("trip").getJSONObject("start_location").getString("name");
        String endName3 = obj3.getJSONObject("trip").getJSONObject("end_location").getString("name");
        long time3 = obj3.getJSONObject("trip").getInt("start_time");
        Double distance3 = obj3.getJSONObject("trip").getDouble("distance_m");
        
        // Card 3
        simpleTextCard= (SimpleTextCard)listCard.childAtIndex(2);        
        simpleTextCard.setHeaderText("Trip 3");
        simpleTextCard.setTitleText("Dist: ".concat(distance3.toString()));
        simpleTextCard.setMessageText(new String[]{startName3, endName3});
        simpleTextCard.setInfoText(((EditText)findViewById(R.id.doc3_info_text)).getText().toString());
        simpleTextCard.setReceivingEvents(((CheckBox)findViewById(R.id.doc3_events_checkbox)).isChecked());
        simpleTextCard.setShowDivider(((CheckBox)findViewById(R.id.doc3_divider_checkbox)).isChecked());
        simpleTextCard.setTimeMillis(time3);
        
        if (((EditText)findViewById(R.id.doc3_menu_options_text)).getText().length() == 0){
            simpleTextCard.setMenuOptions(null); // If all menu options deleted, reset
        }
        else{
            simpleTextCard.setMenuOptions(splitString(((EditText)findViewById(R.id.doc3_menu_options_text)).getText().toString()));
        } 
        
        //Card 4
        
        simpleTextCard= (SimpleTextCard)listCard.childAtIndex(3);        
        simpleTextCard.setHeaderText("Trip 3");
        simpleTextCard.setTitleText("Dist: ".concat(distance3.toString()));
        simpleTextCard.setMessageText(new String[]{startName3, endName3});
        simpleTextCard.setInfoText(((EditText)findViewById(R.id.doc3_info_text)).getText().toString());
        simpleTextCard.setReceivingEvents(((CheckBox)findViewById(R.id.doc3_events_checkbox)).isChecked());
        simpleTextCard.setShowDivider(((CheckBox)findViewById(R.id.doc3_divider_checkbox)).isChecked());
        simpleTextCard.setTimeMillis(time3);
        
        if (((EditText)findViewById(R.id.doc3_menu_options_text)).getText().length() == 0){
            simpleTextCard.setMenuOptions(null); // If all menu options deleted, reset
        }
        else{
            simpleTextCard.setMenuOptions(splitString(((EditText)findViewById(R.id.doc3_menu_options_text)).getText().toString()));
        } 
        
        } catch (Exception e) {
            e.printStackTrace();
          }
    }
    
    
    //
    private String concatStrings(String[] textStrs){
        
        StringBuilder buffy= new StringBuilder();
        
        for (int i= 0; i < textStrs.length; i++){
            
            buffy.append(textStrs[i]);
            
            if (i < (textStrs.length - 1)){
                buffy.append("\n");
            }
        }
        
        return buffy.toString();        
    }
    
    
    //
    private String[] splitString(String textStr){       
        return textStr.split("\n");
    }
  
}