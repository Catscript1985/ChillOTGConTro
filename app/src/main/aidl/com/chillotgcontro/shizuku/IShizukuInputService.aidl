package com.chillotgcontro.shizuku;

import com.chillotgcontro.shizuku.IShizukuEventListener;

interface IShizukuInputService {
    void startGetevent(IShizukuEventListener listener) = 1;
    void stopGetevent() = 2;
    String execute(in String[] command) = 3;
    void destroy() = 16777114;
}
