package fun.cadacrew.roadledger;

import java.util.Calendar;
import java.util.TimeZone;

/** No Android or remote dependencies: run with plain Java 17 in source-check step. */
public final class V71HomeStatsSelfTest {
    private static long date(int year,int month,int day) {
        Calendar c=Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();c.set(year,month-1,day);return c.getTimeInMillis();
    }
    private static void equal(double actual,double expected,String label){
        if(Math.abs(actual-expected)>1e-8)throw new AssertionError(label+": expected "+expected+", was "+actual);
    }
    public static void main(String[] args){
        TimeZone old=TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            V71HomeStats s=new V71HomeStats(date(2026,11,12));
            V71HomeStats.Month[] months=s.months();
            if(months.length!=6||!"Jun".equals(months[0].label)||!"Nov".equals(months[5].label))throw new AssertionError("Month labels");
            s.addFuel(date(2026,11,1),55.50);
            s.addFuel(date(2026,10,31),42);
            s.addExpense(date(2026,11,11),19.50);
            s.addService(date(2026,11,12),100);
            s.addTrip(date(2026,11,12),20);
            s.addFuel(date(2026,5,31),900); // outside: do not contaminate June
            equal(s.currentMonthSpend(),175,"Current spend");
            equal(s.currentMonthTripKm(),20,"Trip distance");
            equal(s.highestMonthSpend(),175,"Maximum");
            equal(months[4].fuel,42,"October bucket");
            equal(months[0].totalSpend(),0,"Empty June");
            if(months[5].endExclusive!=date(2026,12,1))throw new AssertionError("December boundary");
            try{s.addFuel(date(2026,11,12),Double.NaN);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}
            System.out.println("PASS: six calendar month buckets, totals, boundary, isolation and validation");
        } finally {TimeZone.setDefault(old);}
    }
}
