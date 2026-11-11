package fun.cadacrew.roadledger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent, on-device analytics using normalized km and liters; no network. */
public final class RoadLedgerAnalytics {
    private RoadLedgerAnalytics(){}
    public static final class Snapshot {
        public final double totalFuelCost,totalExpenseCost,totalCost,last12MonthsCost,averagePricePerLiter;
        public final double[] sixMonthSpend;
        public final Map<String,Double> costByCategory;
        Snapshot(double fuel,double other,double last12,double price,double[] months,Map<String,Double> categories){
            totalFuelCost=fuel;totalExpenseCost=other;totalCost=fuel+other;
            last12MonthsCost=last12;averagePricePerLiter=price;sixMonthSpend=months.clone();
            costByCategory=java.util.Collections.unmodifiableMap(new LinkedHashMap<>(categories));
        }
    }
    public static Snapshot compute(List<GarageDb.Fuel> fuel,List<GarageDb.Expense> expenses,long now){
        Calendar c=Calendar.getInstance();c.setTimeInMillis(now);
        c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);
        long[] six=new long[6];
        for(int i=5;i>=0;i--){six[i]=c.getTimeInMillis();c.add(Calendar.MONTH,-1);}
        Calendar year=Calendar.getInstance();year.setTimeInMillis(now);year.add(Calendar.MONTH,-12);
        long yearStart=year.getTimeInMillis();
        double totalFuel=0,totalOther=0,yearCost=0,totalLiter=0;
        double[] sixCost=new double[6];
        Map<String,Double> categories=new LinkedHashMap<>();
        for(GarageDb.Fuel f:fuel){
            totalFuel+=f.cost;totalLiter+=f.liters;
            if(f.date>=yearStart&&f.date<=now)yearCost+=f.cost;
            month(six,sixCost,f.date,f.cost,now);
        }
        for(GarageDb.Expense x:expenses){
            totalOther+=x.amount;
            if(x.date>=yearStart&&x.date<=now)yearCost+=x.amount;
            month(six,sixCost,x.date,x.amount,now);
            categories.put(x.category,categories.getOrDefault(x.category,0d)+x.amount);
        }
        return new Snapshot(totalFuel,totalOther,yearCost,totalLiter>0?totalFuel/totalLiter:Double.NaN,sixCost,categories);
    }
    private static void month(long[] starts,double[] totals,long when,double amount,long now){
        if(when>now||when<starts[0])return;
        for(int i=starts.length-1;i>=0;i--){if(when>=starts[i]){totals[i]+=amount;break;}}
    }
}
