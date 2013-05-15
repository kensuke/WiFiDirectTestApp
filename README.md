WiFiDirectTestApp
=================

Android Wi-Fi DirectのAPIを個別に実行し、Wi-Fi Directの動きを見るアプリ。

#### 対応しているインテントフィルタ

* WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION
* WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
* WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION
* WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION

#### 対応しているメソッド

##### Android
* getSystemService

##### WifiP2pManager

* 初期化 initialize


* 発見　discoverPeers
* 発見結果取得　requestPeers


* 接続　connect
* 接続キャンセル　cancelConnect


* グループ生成　createGroup
* グループ削除・切断　removeGroup


* 接続情報取得　requestConnectionInfo
* グループ情報取得　requestGroupInfo


#### やりたい

* 前回実行時の設定状態をPrefへ保存し、次回実行時にリストア
* ログ出力をクラス化
* ログをSDカードへ保存
* ログ文字サイズ変更
* WFD正規表現修正
* ブロードキャストレシーバのコードリファイン
* startActivity()をstartPreferencePanel()へ変更（Wi-Fi Directの画面を表示）
* サービス検出 API追加
* DNS API追加
