package com.chillotgcontro.shizuku;

oneway interface IShizukuEventListener {
    void onLine(String line);
    void onError(String message);
}
