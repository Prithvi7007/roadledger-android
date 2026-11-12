package fun.cadacrew.roadledger;

/** Pure, testable calculations used by the v0.7 screens and notification engine. */
public final class V7Logic {
    private V7Logic() {}
    public static double travelCost(double distanceKm,double quantityPer100Km,double unitPrice){
        finiteNonnegative(distanceKm,"distance");finiteNonnegative(quantityPer100Km,"consumption");finiteNonnegative(unitPrice,"price");
        return (distanceKm/100d)*quantityPer100Km*unitPrice;
    }
    public static double rangeKm(double remainingQuantity,double per100Km){
        finiteNonnegative(remainingQuantity,"remaining quantity");
        if(!Double.isFinite(per100Km)||per100Km<=0)throw new IllegalArgumentException("Consumption must be positive");
        return remainingQuantity*100d/per100Km;
    }
    public static double costPerKm(double total,double distanceKm){
        finiteNonnegative(total,"total");
        return (!Double.isFinite(distanceKm)||distanceKm<=0)?Double.NaN:total/distanceKm;
    }
    public static long nextDueDate(long completedMs,int repeatDays){
        if(completedMs<=0||repeatDays<0||repeatDays>36500)throw new IllegalArgumentException("Invalid recurrence");
        if(repeatDays==0)return 0;
        return Math.addExact(completedMs,Math.multiplyExact((long)repeatDays,86400000L));
    }
    public static double nextDueKm(double completedKm,double repeatKm){
        finiteNonnegative(completedKm,"odometer");finiteNonnegative(repeatKm,"interval");
        return repeatKm==0?0:completedKm+repeatKm;
    }
    public static boolean reminderDue(long now,long dueDate,double currentKm,double dueKm,int warningDays,double warningKm){
        if(warningDays<0||warningKm<0)throw new IllegalArgumentException("Invalid warning window");
        return (dueDate>0&&now>=dueDate-(long)warningDays*86400000L)
            || (dueKm>0&&currentKm>=dueKm-warningKm);
    }
    public static String csv(String value){
        if(value==null)return "\"\"";
        return "\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";
    }
    public static double tripDistance(double startKm,double endKm){
        finiteNonnegative(startKm,"start");finiteNonnegative(endKm,"end");
        if(endKm<startKm)throw new IllegalArgumentException("End odometer must be >= start");
        return endKm-startKm;
    }
    private static void finiteNonnegative(double v,String field){
        if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException("Invalid "+field);
    }
}
