package fun.cadacrew.roadledger;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Native, exportable, independent per-vehicle report. Uses the same tank engine as the UI. */
public final class RoadLedgerReport {
    private RoadLedgerReport(){}
    public static void write(GarageDb db,long vehicleId,long since,long now,OutputStream stream) throws IOException {
        if(stream==null)throw new IOException("Report destination is unavailable");
        GarageDb.Vehicle vehicle=null;
        for(GarageDb.Vehicle v:db.vehicles())if(v.id==vehicleId){vehicle=v;break;}
        if(vehicle==null)throw new IOException("Selected vehicle is missing");
        List<GarageDb.Fuel> fuel=db.fuel(vehicleId);
        List<GarageDb.Expense> expenses=db.expenses(vehicleId);
        List<GarageDb.Trip> trips=db.trips(vehicleId);
        List<RoadLedgerFuelMath.Fill> all=new ArrayList<>();
        for(GarageDb.Fuel f:fuel)all.add(new RoadLedgerFuelMath.Fill(f.id,f.date,f.tankNumber,f.odometer,f.liters,f.cost,f.full,f.missed));
        RoadLedgerFuelMath.Result first=RoadLedgerFuelMath.calculate(all,1);
        RoadLedgerFuelMath.Result second=RoadLedgerFuelMath.calculate(all,2);
        BufferedWriter out=new BufferedWriter(new OutputStreamWriter(stream,StandardCharsets.UTF_8));
        csv(out,"Report","RoadLedger vehicle and fuel economy");
        csv(out,"Vehicle",vehicle.name,"Make",vehicle.make,"Model",vehicle.model);
        csv(out,"Period start",since==0?"All time":date(since),"Period end",date(now));
        csv(out,"Important","Tank economy is calculated from all historical fill-ups; period totals below use the selected date range.");
        csv(out,"Source","Independently calculated full-to-full intervals; incomplete or missed intervals are excluded.");
        csv(out);
        csv(out,"Metric","Tank","Distance km","Quantity in base unit","Base unit","Weighted base unit/100 km","Completed intervals","Displayed economy","Display unit");
        stats(out,first,vehicle,1);stats(out,second,vehicle,2);
        csv(out);
        csv(out,"Record type","Date","Tank","Odometer km","Quantity in base unit","Base unit","Original unit","Net amount","Discount","Full","Missed","Station","Category","Notes");
        double fuelTotal=0,expenseTotal=0,tripDistance=0;
        for(GarageDb.Fuel f:fuel)if(inRange(f.date,since,now)){
            fuelTotal+=f.cost;
            csv(out,"Fuel",date(f.date),f.tankNumber,f.odometer,f.liters,FuelUnits.baseUnit(f.sourceUnit),
                f.sourceUnit,f.cost,f.discount,f.full,f.missed,f.stationId>0?db.stationName(f.stationId):"","", f.fuelSubtype);
        }
        for(GarageDb.Expense e:expenses)if(inRange(e.date,since,now)){
            expenseTotal+=e.amount;
            csv(out,"Expense",date(e.date),"",e.odometer,"","","",e.amount,"","","","",e.category,e.note);
        }
        for(GarageDb.Trip t:trips)if(inRange(t.date,since,now)){
            tripDistance+=t.endKm-t.startKm;
            csv(out,"Trip",date(t.date),"",t.startKm,"","","","","","","","","",t.note);
        }
        csv(out);
        csv(out,"Totals","Fuel cost",fuelTotal,"Non-fuel cost",expenseTotal,"Combined cost",fuelTotal+expenseTotal,"Trip km",tripDistance);
        out.flush();
    }
    private static void stats(BufferedWriter out,RoadLedgerFuelMath.Result s,GarageDb.Vehicle vehicle,int tank) throws IOException {
        String base=FuelUnits.baseUnit(vehicle.tankUnit(tank));
        String display=FuelUnits.consumptionUnit(base,vehicle.consumptionUnit,vehicle.unitSystem);
        csv(out,"Economy",s.tank,s.distanceKm,s.consumedLiters,base,
            Double.isNaN(s.weightedLitersPer100Km)?"N/A":s.weightedLitersPer100Km,s.intervals.size(),
            Double.isNaN(s.weightedLitersPer100Km)?"N/A":FuelUnits.displayConsumption(s.weightedLitersPer100Km,base,display,vehicle.unitSystem),display);
    }
    private static boolean inRange(long when,long from,long to){return when>=from&&when<=to;}
    private static String date(long ms){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(ms));}
    private static void csv(BufferedWriter out,Object... values) throws IOException {
        for(int i=0;i<values.length;i++){
            if(i>0)out.write(',');
            String s=values[i]==null?"":String.valueOf(values[i]);
            if(!s.isEmpty()&&"=+-@".indexOf(s.charAt(0))>=0&&values[i] instanceof CharSequence)s="'"+s;
            out.write('"');out.write(s.replace("\"","\"\""));out.write('"');
        }
        out.write('\n');
    }
}
