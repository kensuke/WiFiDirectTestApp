package com.example.p2p.apitest;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
 * P2P API テストアプリ
 */
public class WiFiDirectTestAppActivity extends Activity {

    /** ログ出力用TAG */
    private final String TAG = "WiFiDirectTestAppActivity";

    /** ログ */
    private TextView mTextView_Log;
    /** 改行 */
    private final String LINE_SEPARATOR = System.getProperty("line.separator");
    private final String LINE_SEPARATOR_HTML = "<br />";
    /** ログをHTML（色付き文字列）で出力
     * TODO 前回実行時の値をPrefへ保存/読込
     */
    private boolean HTML_OUT = true;

    /** BroadcastReceiver */
    private enum ReceiverState {
        All,
        StateChange,
        PeersChange,
        ConnectionChange,
        ThisDeviceChange,
    }

    /** BroadcastReceiver 全部 */
    private BroadcastReceiver mReceiver;
    /** BroadcastReceiver P2P_STATE_CHANGED_ACTION */
    private WDBR_P2P_STATE_CHANGED_ACTION mWDBR_P2P_STATE_CHANGED_ACTION;
    /** BroadcastReceiver P2P_PEERS_CHANGED_ACTION */
    private WDBR_P2P_PEERS_CHANGED_ACTION mWDBR_P2P_PEERS_CHANGED_ACTION;
    /** BroadcastReceiver P2P_CONNECTION_CHANGED_ACTION */
    private WDBR_P2P_CONNECTION_CHANGED_ACTION mWDBR_P2P_CONNECTION_CHANGED_ACTION;
    /** BroadcastReceiver THIS_DEVICE_CHANGED_ACTION */
    private WDBR_P2P_THIS_DEVICE_CHANGED_ACTION mWDBR_THIS_DEVICE_CHANGED_ACTION;

    /** Wi-Fi Direct 有効/無効状態 */
    private boolean mIsWiFiDirectEnabled;

    /** WifiP2pManager */
    private WifiP2pManager mWifiP2pManager;
    /** Channel */
    private Channel mChannel;
    /** peers */
    private List<WifiP2pDevice> mPeers = new ArrayList<WifiP2pDevice>();
    /** リスナアダプタ */
    private ActionListenerAdapter mActionListenerAdapter;

    /** 子リスト保持 */
    private Spinner mPeersSpinner;
    /** 選択中の子 */
    private String mSelectedDevice;

    /* ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
     * Activity API
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);

        String title = "ANDROID_ID[" + getAndroid_ID() + "]";
        title += "   MAC[" + getMACAddress() + "]";
        setTitle(title);

        initializeLog();
        initBroadcastToggle();

        addLog("onCreate()");

        // アプリ内で捕捉していない例外をキャッチし、ログ出力
        //Thread.setDefaultUncaughtExceptionHandler( new UncaughtExceptionHandler() {
        //    public void uncaughtException(Thread thread, Throwable ex) {
        //        addLog("uncaughtException()");
        //        addLog(ex.toString());
        //    }
        //});

        if (!hasP2P()) {
            toastAndLog("onCreate()", "This Device Has Not P2P Feature!!");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        addLog("onResume()");

        // ブロードキャストレシーバで、WIFI_P2P_STATE_CHANGED_ACTIONのコールバックを持ってWi-Fi Direct ON/OFFを判定する
        mIsWiFiDirectEnabled = false;

        // たぶんこのタイミングでブロードキャストレシーバを登録するのがbetter
        registerBroadcastReceiver(ReceiverState.All);
    }

    @Override
    protected void onPause() {
        super.onPause();
        addLog("onPause()");

        // ブロードキャストレシーバ解除
        unRegisterBroadcastReceiver(ReceiverState.All);
    }

    /**
     * ブロードキャストレシーバ登録
     */
    private void registerBroadcastReceiver(ReceiverState rs) {
        IntentFilter filter = new IntentFilter();

        switch (rs) {
        case All:
            filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
            mReceiver = new WiFiDirectBroadcastReceiver();
            registerReceiver(mReceiver, filter);
            addLog("registerBroadcastReceiver() BroadcastReceiver");
            break;

        case StateChange:
            filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            mWDBR_P2P_STATE_CHANGED_ACTION = new WDBR_P2P_STATE_CHANGED_ACTION();
            registerReceiver(mWDBR_P2P_STATE_CHANGED_ACTION, filter);
            addLog("registerBroadcastReceiver() P2P_STATE_CHANGED_ACTION");
            break;

        case PeersChange:
            filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            mWDBR_P2P_PEERS_CHANGED_ACTION = new WDBR_P2P_PEERS_CHANGED_ACTION();
            registerReceiver(mWDBR_P2P_PEERS_CHANGED_ACTION, filter);
            addLog("registerBroadcastReceiver() P2P_PEERS_CHANGED_ACTION");
            break;

        case ConnectionChange:
            filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            mWDBR_P2P_CONNECTION_CHANGED_ACTION = new WDBR_P2P_CONNECTION_CHANGED_ACTION();
            registerReceiver(mWDBR_P2P_CONNECTION_CHANGED_ACTION, filter);
            addLog("registerBroadcastReceiver() P2P_CONNECTION_CHANGED_ACTION");
            break;

        case ThisDeviceChange:
            filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
            mWDBR_THIS_DEVICE_CHANGED_ACTION = new WDBR_P2P_THIS_DEVICE_CHANGED_ACTION();
            registerReceiver(mWDBR_THIS_DEVICE_CHANGED_ACTION, filter);
            addLog("registerBroadcastReceiver() THIS_DEVICE_CHANGED_ACTION");
            break;

        default:
            toastAndLog("registerBroadcastReceiver()", "Unknown ReceiverState["+rs+"]");
            break;
        }
    }

    /**
     * ブロードキャストレシーバ解除
     */
    private void unRegisterBroadcastReceiver(ReceiverState rs) {
        switch (rs) {
        case All:
            if (mReceiver != null) {
                unregisterReceiver(mReceiver);
                mReceiver = null;
                addLog("unRegisterBroadcastReceiver() BroadcastReceiver");
            }
            break;

        case StateChange:
            if (mWDBR_P2P_STATE_CHANGED_ACTION != null) {
                unregisterReceiver(mWDBR_P2P_STATE_CHANGED_ACTION);
                mWDBR_P2P_STATE_CHANGED_ACTION = null;
                addLog("unRegisterBroadcastReceiver() P2P_STATE_CHANGED_ACTION");
            }
            break;

        case PeersChange:
            if (mWDBR_P2P_PEERS_CHANGED_ACTION != null) {
                unregisterReceiver(mWDBR_P2P_PEERS_CHANGED_ACTION);
                mWDBR_P2P_PEERS_CHANGED_ACTION = null;
                addLog("unRegisterBroadcastReceiver() P2P_PEERS_CHANGED_ACTION");
            }
            break;

        case ConnectionChange:
            if (mWDBR_P2P_CONNECTION_CHANGED_ACTION != null) {
                unregisterReceiver(mWDBR_P2P_CONNECTION_CHANGED_ACTION);
                mWDBR_P2P_CONNECTION_CHANGED_ACTION = null;
                addLog("unRegisterBroadcastReceiver() P2P_CONNECTION_CHANGED_ACTION");
            }
            break;

        case ThisDeviceChange:
            if (mWDBR_THIS_DEVICE_CHANGED_ACTION != null) {
                unregisterReceiver(mWDBR_THIS_DEVICE_CHANGED_ACTION);
                mWDBR_THIS_DEVICE_CHANGED_ACTION = null;
                addLog("unRegisterBroadcastReceiver() THIS_DEVICE_CHANGED_ACTION");
            }
            break;

        default:
            toastAndLog("unRegisterBroadcastReceiver()", "Unknown ReceiverState["+rs+"]");
            break;
        }
    }

    /**
     * ブロードキャストレシーバ トグルボタン初期化 入り口
     */
    private void initBroadcastToggle() {
        initBroadcastToggleInner(R.id.toggle_bc_all);
        initBroadcastToggleInner(R.id.toggle_bc_state);
        initBroadcastToggleInner(R.id.toggle_bc_peers);
        initBroadcastToggleInner(R.id.toggle_bc_connection);
        initBroadcastToggleInner(R.id.toggle_bc_this);
    }

    /**
     * ブロードキャストレシーバ トグルボタン初期化
     */
    private void initBroadcastToggleInner(final int rId_Toggle) {
        // トグルボタンのON/OFF変更を検知
        ToggleButton tb = (ToggleButton)findViewById(rId_Toggle);
        tb.setOnCheckedChangeListener( new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ReceiverState rs = ReceiverState.All;
                switch (rId_Toggle) {
                case R.id.toggle_bc_all:
                    rs = ReceiverState.All;
                    break;
                case R.id.toggle_bc_state:
                    rs = ReceiverState.StateChange;
                    break;
                case R.id.toggle_bc_peers:
                    rs = ReceiverState.PeersChange;
                    break;
                case R.id.toggle_bc_connection:
                    rs = ReceiverState.ConnectionChange;
                    break;
                case R.id.toggle_bc_this:
                    rs = ReceiverState.ThisDeviceChange;
                    break;
                default:
                    toastAndLog("initBroadcastToggleInner()", "Unknown ReceiverState["+rs+"]");
                    return;
                }

                if (isChecked) {
                    registerBroadcastReceiver(rs);
                } else {
                    unRegisterBroadcastReceiver(rs);
                }
            }
        });
    }

    /**
     * オプションメニューが最初に呼び出される時に1度だけ呼び出されます
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // メニューアイテムを追加します
        menu.add(Menu.NONE, 0, Menu.NONE, getString(R.string.app_about));
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * オプションメニューアイテムが選択された時に呼び出されます
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean ret = true;
        int id = item.getItemId();
        switch (id) {
        default:
            toastAndLog("onOptionsItemSelected()", "Unknown Item Id["+id+"]");
            break;
        case 0:
            Toast.makeText(this, getAppVersion(), Toast.LENGTH_SHORT).show();
            break;
        }
        return ret;
    }

    /* ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
     * ログ出力
     * TODO ログ関連をごそっとクラス化したほうがおしゃれだと思う
     */

    /**
     * ログ関係初期化
     */
    private void initializeLog() {
        if (mTextView_Log != null) {
            return;
        }

        mTextView_Log = (TextView)findViewById(R.id.textView_log);

        // テキスト変更(=ログ出力追加)を検知
        mTextView_Log.addTextChangedListener( new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // オートスクロール可否チェック
                ToggleButton tb = (ToggleButton)findViewById(R.id.toggle_autoscroll);
                if (!tb.isChecked()) {
                    return;
                }

                // テキスト変更時にログウィンドウを末尾へ自動スクロール
                final ScrollView sv = (ScrollView)findViewById(R.id.scrollview_log);
                sv.post( new Runnable() {
                    public void run() {
                        sv.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void afterTextChanged(Editable s) {}
        });

        // ログ種別(色付き(HTML)、モノクロ)変更検知
        RadioGroup rg = (RadioGroup)findViewById(R.id.radiogroup_logkind);
        rg.setOnCheckedChangeListener( new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                case R.id.radiobutton_html: // 色付き
                    HTML_OUT = true;
                    break;
                case R.id.radiobutton_mono: // モノクロ
                    HTML_OUT = false;
                    break;
                default:
                    addLog("initializeLog() Unknown Log Kind["+checkedId+"]");
                    HTML_OUT = false;
                    break;
                }
            }
        });
    }

    /**
     * ログ出力
     */
    private void addLog(String log) {
        Log.d(TAG, log);

        log = log + nl();
        if (mTextView_Log == null) {
            initializeLog();
        }
        mTextView_Log.append( HTML_OUT ? convHtmlStr2CS(log) : log );
    }

    /**
     * HTMl文字列変換
     */
    private CharSequence convHtmlStr2CS(String htmlStr) {
        return Html.fromHtml(htmlStr);
    }

    /**
     * ログ出力書式に応じた改行コード取得
     */
    private String nl() {
        return HTML_OUT ? LINE_SEPARATOR_HTML : LINE_SEPARATOR;
    }

    /**
     * ”P2Pメソッドのログ出力”用メソッド
     */
    private void addMethodLog(String method) {
        if (HTML_OUT) method = "<font color=lime>"+method+"</font>";
        addLog(nl() + method);
    }

    /**
     * トーストあんどログ
     */
    private void toastAndLog(String msg1, String msg2) {
        String log = msg1 + LINE_SEPARATOR + msg2;
        Toast.makeText(this, log, Toast.LENGTH_SHORT).show();

        if (HTML_OUT) log = "<font color=red>" + msg1 + nl() + msg2 + "</font>";
        addLog(log);
    }

    /**
     * ログリセット
     */
    public void onClickResetLog(View view) {
        mTextView_Log.setText("");
    }

    /**
     * ログ保存
     * TODO ログをSDカードへ保存
     */
    public void onClickSaveLog(View view) {
       String log = mTextView_Log.getText().toString();
       Log.d(TAG, "onClickSaveLog() LOG["+log+"]");
    }

    /**
     * デバイス文字列(ログ出力用)
     */
    private String toStringDevice(WifiP2pDevice device) {
        String log = separateCSV(device.toString()) + nl() + "　" + getDeviceStatus(device.status);
        return HTML_OUT ? "<font color=yellow>"+log+"</font>" : log;
    }

    // ":"区切り文字列へ改行付与
    // " Device: Galaxy_Nexus"+
    // " primary type: 12345-xyz"
    // ↓
    // " Device: Galaxy_Nexus<br />"+
    // " primary type: 12345-xyz<br />"
    private String separateCSV(String csvStr) {
        //return csvStr;
        return csvStr.replaceAll("[^:yWFD] ", nl()+"　"); // ": "、"y " でない半角スペース（＝文頭の半角スペース）にマッチする
        // 以下の”意図しない場所での改行"を除外する
        //"deviceAddress: AB:CD"を"deviceAddress:<br />AB:CD"としない
        //"primary type:"を"primary<br />type:"としない
        //"WFD CtrlPort: 554"を”WFD<br />CtrlPort: 554”としない <= Android 4.2以降のMiracastに対応
    }

    // TODO FIXME 上記正規表現ではWFD情報がうまいこと出力されないバグあり
    //sbuf.append("Device: ").append(deviceName);
    //sbuf.append("\n deviceAddress: ").append(deviceAddress);
    //sbuf.append("\n primary type: ").append(primaryDeviceType);
    //sbuf.append("\n secondary type: ").append(secondaryDeviceType);
    //sbuf.append("\n wps: ").append(wpsConfigMethodsSupported);
    //sbuf.append("\n grpcapab: ").append(groupCapability);
    //sbuf.append("\n devcapab: ").append(deviceCapability);
    //sbuf.append("\n status: ").append(status);
    //sbuf.append("\n wfdInfo: ").append(wfdInfo);
    // wfdInfo: WFD enabled: trueWFD DeviceInfo: 349
    // WFD CtrlPort: 554
    // WFD MaxThroughput: 50

    // デバイス状態文字列変換
    private String getDeviceStatus(int deviceStatus) {
        String status = "";
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                status = "Available";
                break;
            case WifiP2pDevice.INVITED:
                status = "Invited";
                break;
            case WifiP2pDevice.CONNECTED:
                status = "Connected";
                break;
            case WifiP2pDevice.FAILED:
                status = "Failed";
                break;
            case WifiP2pDevice.UNAVAILABLE:
                status = "Unavailable";
                break;
            default:
                status = "Unknown";
                break;
        }
        return HTML_OUT ? "[<b><i><u>"+status+"</u></i></b>]" : "["+status+"]";
    }

    /* ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
     * P2P API
     */

    /**
     * リスナアダプタ
     * WifiP2pManagerクラスの各メソッドは、WifiP2pManager.ActionListenerによって、メソッドの実行結果を知ることができる
     * ただし、successと出たのに失敗したり、failureと出たのに成功したりする
     */
    class ActionListenerAdapter implements WifiP2pManager.ActionListener {

        // 成功
        public void onSuccess() {
            String log = " onSuccess()";
            if (HTML_OUT) log = "<font color=aqua>　"+log+"</font>";
            addLog(log);
        }

        // 失敗
        public void onFailure(int reason) {
            String log = " onFailure("+getReason(reason)+")";
            if (HTML_OUT) log = "<font color=red>　"+log+"</font>";
            addLog(log);
        }

        // 失敗理由intコード -> 文字列変換
        private String getReason(int reason) {
            String[] strs = {"ERROR", "P2P_UNSUPPORTED", "BUSY"};
            try {
                return strs[reason] + "("+reason+")";
            } catch (ArrayIndexOutOfBoundsException e) {
                return "UNKNOWN REASON CODE("+reason+")";
            }
        }
    }

    /**
     * P2Pメソッド実行前のNULLチェック
     */
    private boolean isNull(boolean both) {
        if (mActionListenerAdapter == null) {
            mActionListenerAdapter = new ActionListenerAdapter();
        }

        if (!mIsWiFiDirectEnabled) {
            toastAndLog(" Wi-Fi Direct is OFF!", "try Setting Menu");
            return true;
        }

        if (mWifiP2pManager == null) {
            toastAndLog(" mWifiP2pManager is NULL!", " try getSystemService");
            return true;
        }
        if (both && (mChannel == null) ) {
            toastAndLog(" mChannel is NULL!", " try initialize");
            return true;
        }

        return false;
    }

    /**
     * インスタンス取得
     */
    public void onClickGetSystemService(View view) {
        addMethodLog("getSystemService(Context.WIFI_P2P_SERVICE)");

        mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);

        addLog("　Result["+(mWifiP2pManager != null)+"]");
    }

    /**
     * 初期化
     */
    public void onClickInitialize(View view) {
        addMethodLog("mWifiP2pManager.initialize()");
        if (isNull(false)) { return; }

        mChannel = mWifiP2pManager.initialize(this, getMainLooper(), new ChannelListener() {
            public void onChannelDisconnected() {
                addLog("mWifiP2pManager.initialize() -> onChannelDisconnected()");
            }
        });

        addLog("　Result["+(mChannel != null)+"]");
    }

    /**
     * デバイス発見
     */
    public void onClickDiscoverPeers(View view) {
        addMethodLog("mWifiP2pManager.discoverPeers()");
        if (isNull(true)) { return; }

        mWifiP2pManager.discoverPeers(mChannel, mActionListenerAdapter);
    }

    /**
     * 接続
     */
    public void onClickConnect(View view) {
        addMethodLog("mWifiP2pManager.connect()");
        if (isNull(true)) { return; }

        // ピアが存在しない（、ピア検索をしていない）
        int cnt = mPeers.size();
        if (cnt == 0) {
            addLog(" peer not found! try discoverPeers & requestPeers");
            return;
        }

        // 選択中のデバイス確定
        int idx = 0;
        for (WifiP2pDevice device : mPeers) {
            if (device.deviceName.equals(mSelectedDevice)) {
                break;
            }
            idx += 1;
        }

        // コンフィグオブジェクト生成
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = mPeers.get(idx).deviceAddress;

        // http://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.html#groupOwnerIntent
        // -1 = フレームワークがG.O.を決定する(自分 or 接続先)
        //  0 = 接続先がG.O.になる(可能性が高い)
        // 15 = 自分がG.O.になる(可能性が高い)
        config.groupOwnerIntent = getOwnerIntentValue();

        // http://developer.android.com/reference/android/net/wifi/WpsInfo.html
        // PBC Push button configuration
        // DISPLAY pin method configuration - pin is generated and displayed on device 
        // KEYPAD pin method configuration - pin is entered on device 
        // LABEL pin method configuration - pin is labelled on device 
        // INVALID configuration
        config.wps.setup = getWPSSetupValue();

        addLog(" connecting to ["+mSelectedDevice+"]["+config.deviceAddress+"] G.O.["+config.groupOwnerIntent+"] WPS["+config.wps.setup+"]");

        mWifiP2pManager.connect(mChannel, config, mActionListenerAdapter);
    }

    /**
     * スピナからG.O.インテント値を取得
     */
    private int getOwnerIntentValue() {
        Spinner sp = (Spinner)findViewById(R.id.spinner_go);
        int v = sp.getSelectedItemPosition();
        return v-1; // G.O.の値域は、 -1 <= G.O <= 15
    }

    /**
     * スピナからWps値を取得
     */
    private int getWPSSetupValue() {
        Spinner sp = (Spinner)findViewById(R.id.spinner_wps);
        int v = sp.getSelectedItemPosition();
        switch (v) {
        case 0:
            return WpsInfo.PBC;
        case 1:
            return WpsInfo.DISPLAY;
        case 2:
            return WpsInfo.KEYPAD;
        case 3:
            return WpsInfo.LABEL;
        case 4:
            return WpsInfo.INVALID;
        default:
            toastAndLog("getWPSSetupValue()", "Unknown WPS Index["+v+"]");
            return WpsInfo.INVALID;
        }
    }

    /**
     * 接続キャンセル
     */
    public void onClickCancelConnect(View view) {
        addMethodLog("mWifiP2pManager.cancelConnect()");
        if (isNull(true)) { return; }

        mWifiP2pManager.cancelConnect(mChannel, mActionListenerAdapter);
    }

    /**
     * グループ作成
     */
    public void onClickCreateGroup(View view) {
        addMethodLog("mWifiP2pManager.createGroup()");
        if (isNull(true)) { return; }

        mWifiP2pManager.createGroup(mChannel, mActionListenerAdapter);
    }

    /**
     * グループ削除
     */
    public void onClickRemoveGroup(View view) {
        addMethodLog("mWifiP2pManager.removeGroup()");
        if (isNull(true)) { return; }

        mWifiP2pManager.removeGroup(mChannel, mActionListenerAdapter);
    }

    /**
     * 接続情報要求
     */
    public void onClickRequestConnectionInfo(View view) {
        addMethodLog("mWifiP2pManager.requestConnectionInfo()");
        if (isNull(true)) { return; }

        mWifiP2pManager.requestConnectionInfo(mChannel, new ConnectionInfoListener() {
            // requestConnectionInfo()実行後、非同期応答あり
            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                addLog("　onConnectionInfoAvailable():");
                if (info == null) {
                    addLog("  info is NULL!");
                    return;
                }
                addLog("  groupFormed:" + info.groupFormed);
                addLog("  isGroupOwner:" + info.isGroupOwner);
                addLog("  groupOwnerAddress:" + info.groupOwnerAddress);
            }
        });
    }

    /**
     * グループ情報要求
     */
    public void onClickRequestGroupInfo(View view) {
        addMethodLog("mWifiP2pManager.requestGroupInfo()");
        if (isNull(true)) { return; }

        mWifiP2pManager.requestGroupInfo(mChannel, new GroupInfoListener() {
            // requestGroupInfo()実行後、非同期応答あり
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                addLog("　onGroupInfoAvailable():");
                if (group == null) {
                    addLog("  group is NULL!");
                    return;
                }

                String log = separateCSV(group.toString());

                // パスワードは、G.O.のみ取得可能
                String pass = nl() + "　password: ";
                if (group.isGroupOwner()) {
                    pass += group.getPassphrase();
                } else {
                    pass += "Client Couldn't Get Password";
                }
                if (HTML_OUT) pass = "<font color=red><b>"+pass+"</b></font>"; // たぶんfont colorのネストはできない(パスワードが赤にならない)
                log += pass;
                if (HTML_OUT) log = "<font color=#fffacd>"+log+"</font>"; // color=lemonchiffon
                addLog(log);
            }
        });
    }

    /**
     * ピアリスト要求
     */
    public void onClickRequestPeers(View view) {
        addMethodLog("mWifiP2pManager.requestPeers()");
        if (isNull(true)) { return; }

        mWifiP2pManager.requestPeers(mChannel, new PeerListListener() {
            // requestPeers()実行後、非同期応答あり
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                mPeers.clear();
                mPeers.addAll(peers.getDeviceList());
                int cnt = mPeers.size();
                addLog("　onPeersAvailable() : num of peers["+cnt+"]");
                for (int i = 0; i < cnt; i++) {
                    addLog(nl() + " ***********["+i+"]***********");
                    addLog("  " + toStringDevice(mPeers.get(i)));
                }

                updatePeersSpinner();
            }
        });
    }

    /**
     * 子リストUI処理
     */
    private void updatePeersSpinner() {
        // スピナーインスタンス取得、アイテム選択イベントハンドラ設定
        if (mPeersSpinner == null) {
            mPeersSpinner = (Spinner)findViewById(R.id.spinner_peers);
            mPeersSpinner.setOnItemSelectedListener( new OnItemSelectedListener() {
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    mSelectedDevice = mPeers.get(position).deviceName;
                    addLog(nl() + "Selected Peer["+mSelectedDevice+"]");
                }

                public void onNothingSelected(AdapterView<?> arg0) {}
            });
        }

        // 子リストからデバイス名の配列を生成し、スピナーのアイテムとして設定
        int cnt = mPeers.size();
        String[] peers = new String[cnt];
        for (int i = 0; i < cnt; i++) {
            peers[i] = mPeers.get(i).deviceName;
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, peers);
        mPeersSpinner.setAdapter(adapter);
    }

    /* ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
     * ブロードキャストレシーバ
     * TODO デコレータパターンを適用できそう？？（あまり興味ない）
     * レシーバの登録/解除メソッドを含めて見直す余地はありそう
     */

    /**
     * ブロードキャストレシーバ 全部
     */
    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String log = "onReceive() ["+action+"]";
            if (HTML_OUT) log = "<font color=fuchsia>"+log+"</font>";
            addLog(nl() + log);

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                mIsWiFiDirectEnabled = false;
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                String sttStr;
                switch (state) {
                case WifiP2pManager.WIFI_P2P_STATE_ENABLED:
                    mIsWiFiDirectEnabled = true;
                    sttStr = "ENABLED";
                    break;
                case WifiP2pManager.WIFI_P2P_STATE_DISABLED:
                    sttStr = "DISABLED";
                    break;
                default:
                    sttStr = "UNKNOWN";
                    break;
                }
                addLog("state["+sttStr+"]("+state+")");
                changeBackgroundColor();
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // このタイミングでrequestPeers()を呼び出すと、peerの変化(ステータス変更とか)がわかる
                // 本テストアプリは、メソッド単位での実行をテストしたいので、ここではrequestPeers()を実行しない
                addLog("try requestPeers()");
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                // networkInfo.toString()はCSV文字列(1行)を返す。そのままでは読みにくいので、カンマを改行へ変換する。
                String nlog = networkInfo.toString().replaceAll(",", nl()+"　");
                if (HTML_OUT) nlog = "<font color=#f0e68c>"+nlog+"</font>"; // khaki
                addLog(nlog);
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                addLog(toStringDevice(device));
            }
        }
    }

    /**
     * ブロードキャストレシーバ P2P ON/OFF状態変更検知
     */
    public class WDBR_P2P_STATE_CHANGED_ACTION extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String log = "onReceive() ["+action+"]";
            if (HTML_OUT) log = "<font color=fuchsia>"+log+"</font>";
            addLog(nl() + log);

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                mIsWiFiDirectEnabled = false;
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                String sttStr;
                switch (state) {
                case WifiP2pManager.WIFI_P2P_STATE_ENABLED:
                    mIsWiFiDirectEnabled = true;
                    sttStr = "ENABLED";
                    break;
                case WifiP2pManager.WIFI_P2P_STATE_DISABLED:
                    sttStr = "DISABLED";
                    break;
                default:
                    sttStr = "UNKNOWN";
                    break;
                }
                addLog("state["+sttStr+"]("+state+")");
                changeBackgroundColor();
            }
        }
    }

    /**
     * ブロードキャストレシーバ 周辺のP2Pデバイス検出
     */
    public class WDBR_P2P_PEERS_CHANGED_ACTION extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String log = "onReceive() ["+action+"]";
            if (HTML_OUT) log = "<font color=fuchsia>"+log+"</font>";
            addLog(nl() + log);

            if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                // このタイミングでrequestPeers()を呼び出すと、peerの変化(ステータス変更とか)がわかる
                // 本テストアプリは、メソッド単位での実行をテストしたいので、ここではrequestPeers()を実行しない
                addLog("try requestPeers()");
            }
        }
    }

    /**
     * ブロードキャストレシーバ 接続状態変更(CONNECT/DISCONNECT)検知
     */
    public class WDBR_P2P_CONNECTION_CHANGED_ACTION extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String log = "onReceive() ["+action+"]";
            if (HTML_OUT) log = "<font color=fuchsia>"+log+"</font>";
            addLog(nl() + log);

            if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                // networkInfo.toString()はCSV文字列(1行)を返す。そのままでは読みにくいので、カンマを改行へ変換する。
                String nlog = networkInfo.toString().replaceAll(",", nl()+"　");
                if (HTML_OUT) nlog = "<font color=#f0e68c>"+nlog+"</font>"; // khaki
                addLog(nlog);
            }
        }
    }

    /**
     * ブロードキャストレシーバ 自端末検知
     */
    public class WDBR_P2P_THIS_DEVICE_CHANGED_ACTION extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String log = "onReceive() ["+action+"]";
            if (HTML_OUT) log = "<font color=fuchsia>"+log+"</font>";
            addLog(nl() + log);

            if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                WifiP2pDevice device = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                addLog(toStringDevice(device));
            }
        }
    }

    /**
     * APIボタンエリアの背景色をWi-Fi Direct有効/無効フラグで色分けする
     * 有効 青
     * 無効 赤
     */
    private void changeBackgroundColor() {
        ScrollView sc = (ScrollView)findViewById(R.id.layout_apibuttons);
        sc.setBackgroundColor( mIsWiFiDirectEnabled ? Color.BLUE : Color.RED );
    }

    /* ★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★★
     * 割りとどうでもいいメソッド
     */

    /**
     * ANDROID_ID取得
     */
    private String getAndroid_ID() {
        return Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Wi-Fi MACアドレス取得
     */
    private String getMACAddress() {
        WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = manager.getConnectionInfo();
        String mac = wifiInfo.getMacAddress();
        return mac;
    }

    /**
     * アプリバージョン取得
     */
    private String getAppVersion() {
        PackageInfo packageInfo = null;
        try {
            packageInfo = getPackageManager().getPackageInfo("com.example.p2p.apitest", PackageManager.GET_META_DATA);
            String ver = "versionCode : "+packageInfo.versionCode+" / "+"versionName : "+packageInfo.versionName;
            return ver;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 端末はp2p featureを持っているか？
     */
    private boolean hasP2P() {
        return getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
    }

    /**
     * Wi-Fi Direct 設定画面表示
     */
    public void onClickGotoWiFiSetting(View view) {
        String pac = "com.android.settings";
        Intent i = new Intent();

        // まずはこの画面を出してみる(Galaxy Nexus 4.0はこれだったと思う)
        i.setClassName(pac, pac + ".wifi.p2p.WifiP2pSettings");
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "onClickGotoWiFiSetting() " + e.getMessage());
            // 17 4.2 JELLY_BEAN_MR1 
            // 16 4.1, 4.1.1 JELLY_BEAN
            // 15 4.0.3, 4.0.4 ICE_CREAM_SANDWICH_MR1
            // 14 4.0, 4.0.1, 4.0.2 ICE_CREAM_SANDWICH
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH+1) { // 14, 15 = ICS
                startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); // ICSの場合は、たぶん、"Wi-Fi→その他"にWi-Fi DirectのON/OFFがあると思う
            } else {
                i.setClassName(pac, pac + ".wifi.WifiSettings"); // その他(JB)の場合はとりあえずWi-Fi設定画面を出しておく^^;
                // TODO 4.1以降は、startActivity()ではなく、startPreferencePanel()なのかも
                //if (getActivity() instanceof PreferenceActivity) {
                //    ((PreferenceActivity) getActivity()).startPreferencePanel(
                //         WifiP2pSettings.class.getCanonicalName(), null, R.string.wifi_p2p_settings_title, null, this, 0);
                try {
                    startActivity(i);
                    Toast.makeText(this, "TRY menu -> Wi-Fi Direct", Toast.LENGTH_LONG).show();
                } catch (ActivityNotFoundException e2) {
                    Log.e(TAG, "onClickGotoWiFiSetting() " + e2.getMessage());
                }
            }
        }
    }

    /**
     * ICSかつAndroidのソースツリー内でビルドした場合のにみ有効
     * enable/disableはhideメソッドなため、通常のアプリビルドだとメソッドが見えない
     * かつ、JB以降ではenable/disableはたぶん排除された・・
     */
    public void onClickEnable(View view) {
        if (isNull(true)) {
            return;
        }
        if (mIsWiFiDirectEnabled) {
            //mWifiP2pManager.disableP2p(mChannel);
            Log.w(TAG, "onClickEnable() Skip disableP2p()");
        } else {
            //mWifiP2pManager.enableP2p(mChannel);
            Log.w(TAG, "onClickEnable() Skip enableP2p()");
        }
    }

}