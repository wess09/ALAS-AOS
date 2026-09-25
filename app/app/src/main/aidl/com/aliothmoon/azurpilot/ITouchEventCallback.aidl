// ITouchEventCallback.aidl
package com.aliothmoon.azurpilot;

// Declare any non-default types here with import statements

oneway interface ITouchEventCallback {
   void onCallback(int x,int y, int type);
}