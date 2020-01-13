package com.harper.asteroids;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public  class DateValidator {

    public static boolean isWithinNextWeek(Date date){
        Calendar firstDayofNewWeek = Calendar.getInstance();
        Calendar lastDayofNewWeek = Calendar.getInstance();
        firstDayofNewWeek.set(Calendar.HOUR_OF_DAY, 0);
        firstDayofNewWeek.set(Calendar.MINUTE, 0);
        firstDayofNewWeek.set(Calendar.SECOND, 0);
        lastDayofNewWeek.setTime(firstDayofNewWeek.getTime());
        lastDayofNewWeek.add(Calendar.DATE,7);
        Date firstDay = firstDayofNewWeek.getTime();
        Date lastDay = lastDayofNewWeek.getTime();
        if(date.after(firstDayofNewWeek.getTime()) && date.before(lastDayofNewWeek.getTime())){
            return true;
        }
        return false;

    }
}
