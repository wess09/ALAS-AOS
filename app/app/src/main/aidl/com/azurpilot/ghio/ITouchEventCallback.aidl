// ITouchEventCallback.aidl
package com.azurpilot.ghio;

// Declare any non-default types here with import statements

oneway interface ITouchEventCallback {
   void onCallback(int x,int y, int type);
}