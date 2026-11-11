package fun.cadacrew.roadledger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Independently specified, tank-aware full-to-full fuel mathematics.
 * All inputs are km and liters and are never mixed between tanks.
 * Does not attempt Fuelio's optional virtual-full-tank estimator.
 */
public final class RoadLedgerFuelMath {
    private RoadLedgerFuelMath() {}
    public static final class Fill {
        public final long id, date;
        public final int tank;
        public final double km, liters, paid;
        public final boolean full, missed;
        public Fill(long id, long date, int tank, double km, double liters, double paid, boolean full, boolean missed) {
            if (tank != 1 && tank != 2) throw new IllegalArgumentException("Tank must be 1 or 2");
            if (km < 0 || liters <= 0 || paid < 0 || !Double.isFinite(km) || !Double.isFinite(liters) || !Double.isFinite(paid))
                throw new IllegalArgumentException("Invalid fill-up quantity");
            this.id=id; this.date=date; this.tank=tank; this.km=km; this.liters=liters;
            this.paid=paid; this.full=full; this.missed=missed;
        }
    }
    public static final class Interval {
        public final long fromId, toId;
        public final int tank;
        public final double distanceKm, fuelLiters, litersPer100Km;
        Interval(long from, long to, int tank, double distance, double fuel) {
            fromId=from;toId=to;this.tank=tank;distanceKm=distance;fuelLiters=fuel;
            litersPer100Km=100.0*fuel/distance;
        }
    }
    public static final class Result {
        public final int tank;
        public final double distanceKm, consumedLiters, weightedLitersPer100Km;
        public final List<Interval> intervals;
        Result(int tank, double km, double liters, List<Interval> intervals) {
            this.tank=tank;distanceKm=km;consumedLiters=liters;
            weightedLitersPer100Km=km>0?100*liters/km:Double.NaN;
            this.intervals=Collections.unmodifiableList(new ArrayList<>(intervals));
        }
    }
    public static Result calculate(List<Fill> fillups, int tank) {
        if(tank!=1&&tank!=2)throw new IllegalArgumentException("Tank must be 1 or 2");
        List<Fill> ordered=new ArrayList<>();
        for(Fill fill:fillups)if(fill.tank==tank)ordered.add(fill);
        ordered.sort(Comparator.comparingDouble((Fill f)->f.km).thenComparingLong(f->f.date).thenComparingLong(f->f.id));
        Fill anchor=null;
        double litersSince=0, kmTotal=0, litersTotal=0;
        List<Interval> intervals=new ArrayList<>();
        double lastKm=Double.NEGATIVE_INFINITY;
        for(Fill f:ordered){
            // Ambiguous duplicate odometer and missed records break the interval:
            // fail closed rather than reporting an artificially optimistic economy.
            if(f.missed || f.km<=lastKm) {
                anchor=null;litersSince=0;
                lastKm=f.km;
                if(!f.missed && f.full)anchor=f;
                continue;
            }
            lastKm=f.km;
            if(anchor==null){if(f.full)anchor=f;continue;}
            litersSince+=f.liters;
            if(f.full){
                double distance=f.km-anchor.km;
                if(distance>0.1 && litersSince>0){
                    Interval interval=new Interval(anchor.id,f.id,tank,distance,litersSince);
                    intervals.add(interval);kmTotal+=distance;litersTotal+=litersSince;
                }
                anchor=f;litersSince=0;
            }
        }
        return new Result(tank,kmTotal,litersTotal,intervals);
    }
    public static double mpgUs(double litersPer100Km) {
        return litersPer100Km>0?235.214583/litersPer100Km:Double.NaN;
    }
    public static double mpgImperial(double litersPer100Km) {
        return litersPer100Km>0?282.480936/litersPer100Km:Double.NaN;
    }
}
