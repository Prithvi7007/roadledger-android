package fun.cadacrew.roadledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import java.io.InputStream;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int EXPORT_REQUEST=1042;
    private static final int IMPORT_REQUEST=1043;
    private static final int BACKUP_REQUEST=1044;
    private static final int RESTORE_REQUEST=1045;
    private static final int REPORT_REQUEST=1046;
    private static final int SOURCE_EXPORT_REQUEST=1047;
    private long reportVehicleId=-1, reportSince=0;
    private String archiveToExport="";
    private static final double KM_PER_MILE=1.609344;
    private static final double LITERS_PER_GALLON=3.785411784;
    private static final String[] TABS={"Home","Fuel","Insights","Service","More"};
    private GarageDb db;
    private long selectedVehicle=-1;
    private int tab=0;
    private LinearLayout body;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db=new GarageDb(this);
        if(state!=null){
            selectedVehicle=state.getLong("selectedVehicle",-1);
            tab=state.getInt("tab",0);
            reportVehicleId=state.getLong("reportVehicleId",-1);
            reportSince=state.getLong("reportSince",0);
            archiveToExport=state.getString("archiveToExport","");
        }
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putLong("selectedVehicle",selectedVehicle);
        out.putInt("tab",tab);
        out.putLong("reportVehicleId",reportVehicleId);
        out.putLong("reportSince",reportSince);
        out.putString("archiveToExport",archiveToExport);
        super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() {
        if(db!=null) db.close();
        super.onDestroy();
    }
    private GarageDb.Vehicle active() {
        List<GarageDb.Vehicle> v=db.vehicles();
        for(GarageDb.Vehicle x:v) if(x.id==selectedVehicle) return x;
        selectedVehicle=v.isEmpty()?-1:v.get(0).id;
        return v.isEmpty()?null:v.get(0);
    }
    private void render() {
        GarageDb.Vehicle vehicle=active();
        LinearLayout shell=Ui.col(this);
        shell.setBackgroundColor(Ui.BG);
        shell.setFitsSystemWindows(true);

        LinearLayout header=Ui.col(this);
        header.setPadding(Ui.dp(this,22),Ui.dp(this,22),Ui.dp(this,22),Ui.dp(this,11));
        LinearLayout brand=Ui.row(this);
        TextView name=Ui.label(this,"ROADLEDGER",25,Ui.INK,true);
        name.setLetterSpacing(.085f);
        brand.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=Ui.label(this,"◆  PRIVATE",11,Ui.MINT,true);
        badge.setPadding(Ui.dp(this,10),Ui.dp(this,8),Ui.dp(this,10),Ui.dp(this,8));
        badge.setBackground(Ui.shape(Ui.CARD_ALT,this,12));
        brand.addView(badge);
        header.addView(brand);
        header.addView(Ui.label(this,"BUILT FOR THE NIGHT DRIVE",12,Ui.MUTED,true),Ui.below(this,2));
        TextView selector=Ui.label(this,vehicle==null?"＋ Add your first vehicle": "▾  "+vehicle.name+"   ·   "+join(vehicle.make,vehicle.model),13,Ui.INK,true);
        selector.setBackground(Ui.shape(Ui.CARD_ALT,this,13));
        selector.setPadding(Ui.dp(this,16),Ui.dp(this,12),Ui.dp(this,16),Ui.dp(this,12));
        selector.setOnClickListener(v->chooseVehicle());
        header.addView(selector,Ui.below(this,18));
        shell.addView(header);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        body=Ui.col(this);
        body.setPadding(Ui.dp(this,20),Ui.dp(this,9),Ui.dp(this,20),Ui.dp(this,30));
        scroll.addView(body);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(vehicle==null) emptyScreen();
        else if(tab==0) overview(vehicle);
        else if(tab==1) fuelScreen(vehicle);
        else if(tab==2) insightsScreen(vehicle);
        else if(tab==3) serviceScreen(vehicle);
        else if(tab==4) moreScreen(vehicle);
        else if(tab==5) expenseScreen(vehicle);
        else if(tab==6) tripScreen(vehicle);
        else if(tab==7) garageScreen(vehicle);
        else timelineScreen(vehicle);
        bottomNavigation(shell);
        setContentView(shell);
    }
    private void bottomNavigation(LinearLayout shell) {
        LinearLayout nav=Ui.row(this);
        nav.setPadding(Ui.dp(this,7),Ui.dp(this,10),Ui.dp(this,7),Ui.dp(this,12));
        nav.setBackgroundColor(Ui.CARD);
        String[] icons={"◉","◈","▥","⌁","▦"};
        for(int i=0;i<TABS.length;i++){
            final int target=i;
            LinearLayout cell=Ui.col(this);
            cell.setGravity(Gravity.CENTER);
            boolean selected=(tab==i)||(i==4&&tab>4);
            TextView icon=Ui.label(this,icons[i],22,selected?Ui.MINT:Ui.MUTED,true);
            icon.setGravity(Gravity.CENTER);
            TextView label=Ui.label(this,TABS[i],10,selected?Ui.MINT:Ui.MUTED,selected);
            label.setGravity(Gravity.CENTER);
            cell.addView(icon);
            cell.addView(label);
            cell.setPadding(0,Ui.dp(this,3),0,Ui.dp(this,3));
            if(selected) cell.setBackground(Ui.shape(Ui.CARD_ALT,this,14));
            cell.setOnClickListener(v->{tab=target;render();});
            nav.addView(cell,new LinearLayout.LayoutParams(0,Ui.dp(this,59),1));
        }
        shell.addView(nav);
    }
    private void emptyScreen(){
        section("Welcome to the garage","Every mile has a story.");
        LinearLayout card=Ui.card(this);
        card.addView(Ui.label(this,"Your garage awaits",22,Ui.INK,true));
        card.addView(Ui.label(this,"Add a vehicle or restore your history from a Fuelio CSV backup. Your records stay on your device.",14,Ui.MUTED,false),Ui.below(this,10));
        android.widget.Button b=Ui.primary(this,"＋  Add a vehicle");
        b.setOnClickListener(v->vehicleDialog());
        card.addView(b,Ui.below(this,22));
        android.widget.Button importButton=Ui.secondary(this,"↓  Import Fuelio backup");
        importButton.setOnClickListener(w->importData());
        card.addView(importButton,Ui.below(this,10));
        android.widget.Button restore=Ui.secondary(this,"⤒  Restore a RoadLedger backup");
        restore.setOnClickListener(w->chooseRestore());
        card.addView(restore,Ui.below(this,10));
        body.addView(card,Ui.below(this,15));
        note("BLACK + GOLD  ·  NO ACCOUNT  ·  NO ADS  ·  NO TRACKING");
    }
    private void overview(GarageDb.Vehicle v){
        section("Command center","Your vehicle, under control");
        List<GarageDb.Fuel> fuel=db.fuel(v.id);
        List<GarageDb.Expense> costs=db.expenses(v.id);
        List<GarageDb.Trip> trips=db.trips(v.id);
        GarageDb.Economy economy=db.economy(v.id);
        double fuelSpend=0,maintenance=0,totalTripKm=0;
        for(GarageDb.Fuel f:fuel) fuelSpend+=f.cost;
        for(GarageDb.Expense e:costs) maintenance+=e.amount;
        for(GarageDb.Trip t:trips) totalTripKm+=t.endKm-t.startKm;

        LinearLayout r1=Ui.row(this);
        r1.addView(metric("AVERAGE ECONOMY",economyValue(v,economy,1),economyLabel(v,1),Ui.MINT),weighted());
        r1.addView(metric("FUEL SPEND",usd(fuelSpend),fuel.size()+" fill-ups",Ui.BLUE),weighted(12));
        body.addView(r1,Ui.below(this,18));
        LinearLayout r2=Ui.row(this);
        r2.addView(metric("OTHER EXPENSES",usd(maintenance),costs.size()+" entries",Ui.ORANGE),weighted());
        r2.addView(metric("TRIP DISTANCE",fmt(totalTripKm/unitFactor(v))+" "+distUnit(v),trips.size()+" trips",Ui.MINT),weighted(12));
        body.addView(r2,Ui.below(this,12));

        LinearLayout chart=Ui.card(this);
        chart.addView(Ui.label(this,"Fuel economy trend",19,Ui.INK,true));
        chart.addView(Ui.label(this,"Full-to-full fuel economy · "+economyLabel(v,1),12,Ui.MUTED,false),Ui.below(this,4));
        List<Double> chartValues=new ArrayList<>();
        for(Double value:economy.intervalValues)chartValues.add(FuelUnits.displayConsumption(value,FuelUnits.baseUnit(v.tankUnit(1)),v.consumptionUnit,v.unitSystem));
        chart.addView(new Ui.EconomyChart(this,chartValues),Ui.below(this,12));
        if(economy.intervalValues.isEmpty()) chart.addView(Ui.label(this,"Record two full tanks to calculate meaningful consumption.",12,Ui.MUTED,false));
        else chart.addView(Ui.label(this,fmt(economy.totalDistanceKm/unitFactor(v))+" "+distUnit(v)+"  ·  "+fmt(economy.totalLiters/fuelFactor(v))+" "+fuelUnit(v)+" in completed intervals",12,Ui.MUTED,false));
        body.addView(chart,Ui.below(this,18));

        LinearLayout actions=Ui.row(this);
        android.widget.Button fuelButton=Ui.primary(this,"＋  Fill-up");
        fuelButton.setOnClickListener(w->fuelDialog(v));
        actions.addView(fuelButton,weighted());
        android.widget.Button costButton=Ui.secondary(this,"＋  Expense");
        costButton.setOnClickListener(w->expenseDialog(v));
        actions.addView(costButton,weighted(12));
        body.addView(actions,Ui.below(this,18));
        quickLinks();
        android.widget.Button settings=Ui.secondary(this,"⚙  Vehicle, fuel and units");
        settings.setOnClickListener(w->vehiclePreferencesDialog(v));
        body.addView(settings,Ui.below(this,12));

        if(!fuel.isEmpty()){
            body.addView(Ui.label(this,"RECENT ACTIVITY",12,Ui.MUTED,true),Ui.below(this,25));
            int count=Math.min(3,fuel.size());
            for(int i=0;i<count;i++) fuelRecord(fuel.get(i),v);
        }
        android.widget.Button export=Ui.secondary(this,"↓  Export all your data as CSV");
        export.setOnClickListener(w->exportData());
        body.addView(export,Ui.below(this,25));
        android.widget.Button importBackup=Ui.secondary(this,"↓  Import Fuelio history");
        importBackup.setOnClickListener(w->importData());
        body.addView(importBackup,Ui.below(this,12));
        note("Black + gold mode  ·  Your data stays on your device.");
    }
    private void jump(int page){tab=page;render();}
    private void quickLinks(){
        body.addView(Ui.label(this,"EXPLORE YOUR VEHICLE",12,Ui.MUTED,true),Ui.below(this,24));
        LinearLayout first=Ui.row(this);
        android.widget.Button insights=Ui.secondary(this,"◈  Insights");
        insights.setOnClickListener(w->jump(2));first.addView(insights,weighted());
        android.widget.Button service=Ui.secondary(this,"⌁  Service" );
        service.setOnClickListener(w->jump(3));first.addView(service,weighted(10));
        body.addView(first,Ui.below(this,9));
        LinearLayout second=Ui.row(this);
        android.widget.Button expenses=Ui.secondary(this,"▤  Expenses");
        expenses.setOnClickListener(w->jump(5));second.addView(expenses,weighted());
        android.widget.Button trips=Ui.secondary(this,"↗  Trips");
        trips.setOnClickListener(w->jump(6));second.addView(trips,weighted(10));
        body.addView(second,Ui.below(this,9));
    }
    private void moreScreen(GarageDb.Vehicle v){
        section("Explore", "Your records, maintenance, stations and data tools.");
        addButton("▤  Expense history",()->jump(5));
        addButton("↗  Trip history",()->jump(6));
        addButton("◷  All activity timeline",()->jump(8));
        addButton("▦  Vehicles, stations and backups",()->jump(7));
        addButton("↓  Import Fuelio data",this::importData);
        LinearLayout card=Ui.card(this);
        card.addView(Ui.label(this,"YOUR DATA STAYS LOCAL",13,Ui.MINT,true));
        card.addView(Ui.label(this,"Use Garage to manage fuel stations, custom expense categories, native ZIP backups, and restoration.",13,Ui.MUTED,false),Ui.below(this,6));
        body.addView(card,Ui.below(this,17));
    }
    private void insightsScreen(GarageDb.Vehicle v){
        section("Vehicle insights","Costs, efficiency and trends that help you plan.");
        android.widget.Button report=Ui.secondary(this,"↓ Export detailed vehicle report (CSV)");
        report.setOnClickListener(w->selectReportPeriod(v));
        body.addView(report,Ui.below(this,10));
        GarageDb.Economy second=db.economy(v.id,2);
        boolean hasTank2=false;for(GarageDb.Fuel f:db.fuel(v.id))if(f.tankNumber==2){hasTank2=true;break;}
        if(hasTank2)summaryNote(Double.isNaN(second.litersPer100Km)?"Tank 2 needs full fill-ups":
            "Tank 2: "+economyValue(v,second,2)+" "+economyLabel(v,2),
            "Calculated separately from primary tank · "+second.intervalValues.size()+" complete intervals");
        RoadLedgerAnalytics.Snapshot s=RoadLedgerAnalytics.compute(db.fuel(v.id),db.expenses(v.id),System.currentTimeMillis());
        GarageDb.Economy e=db.economy(v.id);
        LinearLayout first=Ui.row(this);
        first.addView(metric("ALL-TIME COST",usd(s.totalCost),"Fuel and non-fuel",Ui.MINT),weighted());
        first.addView(metric("FUEL UNIT PRICE",hasTank2?"—":s.averagePricePerLiter>0?usd(s.averagePricePerLiter*FuelUnits.factor(v.tankUnit(1))):"—",
            hasTank2?"Separate tanks have different price units":"Average $ / "+v.tankUnit(1),Ui.BLUE),weighted(10));
        body.addView(first,Ui.below(this,18));
        LinearLayout secondRow=Ui.row(this);
        secondRow.addView(metric("COST PER DISTANCE",hasTank2?"—":e.totalDistanceKm>0?usd(s.totalCost/(e.totalDistanceKm/unitFactor(v))):"—",
            hasTank2?"Not combined across two tanks":"$ / "+distUnit(v)+" (completed fuel intervals)",Ui.ORANGE),weighted());
        secondRow.addView(metric("LAST 12 MONTHS",usd(s.last12MonthsCost),"Fuel + expenses",Ui.MINT),weighted(10));
        body.addView(secondRow,Ui.below(this,10));
        LinearLayout spending=Ui.card(this);
        spending.addView(Ui.label(this,"Monthly spending",19,Ui.INK,true));
        spending.addView(Ui.label(this,"Fuel and other costs · last 6 calendar months",12,Ui.MUTED,false),Ui.below(this,5));
        List<Double> monthly=new ArrayList<>();
        for(double number:s.sixMonthSpend)monthly.add(number);
        spending.addView(new Ui.EconomyChart(this,monthly),Ui.below(this,8));
        body.addView(spending,Ui.below(this,17));
        LinearLayout breakdown=Ui.card(this);
        breakdown.addView(Ui.label(this,"Where your money goes",18,Ui.INK,true));
        breakdown.addView(Ui.label(this,"Fuel  ·  "+usd(s.totalFuelCost),15,Ui.MINT,true),Ui.below(this,11));
        for(java.util.Map.Entry<String,Double> item:s.costByCategory.entrySet()){
            breakdown.addView(Ui.label(this,item.getKey()+"  ·  "+usd(item.getValue()),13,Ui.INK,false),Ui.below(this,7));
        }
        if(s.costByCategory.isEmpty())breakdown.addView(Ui.label(this,"No expense categories yet",13,Ui.MUTED,false),Ui.below(this,8));
        body.addView(breakdown,Ui.below(this,16));
        LinearLayout recent=Ui.card(this);
        recent.addView(Ui.label(this,"Recent fill-up prices",18,Ui.INK,true));
        int limit=0;
        for(GarageDb.Fuel f:db.fuel(v.id)){
            if(limit++>=6)break;
            recent.addView(Ui.label(this,day(f.date)+" · Tank "+f.tankNumber+" · "+usd(f.cost/FuelUnits.fromBase(f.liters,v.tankUnit(f.tankNumber)))+" / "+v.tankUnit(f.tankNumber),13,Ui.INK,false),Ui.below(this,8));
        }
        if(limit==0)recent.addView(Ui.label(this,"Add fill-ups to see price history",13,Ui.MUTED,false),Ui.below(this,9));
        body.addView(recent,Ui.below(this,16));
        note("Full-to-full intervals determine measured consumption. Tank energy units are never converted into incompatible fuel dimensions.");
    }
    private void serviceScreen(GarageDb.Vehicle v){
        section("Service schedule","Maintenance deadlines at a glance.");
        addButton("＋  Add service reminder",()->serviceDialog(v,null));
        double odo=db.currentOdometerKm(v.id);
        summaryNote(db.overdueServices(v.id,System.currentTimeMillis())+" overdue",fmt(odo/unitFactor(v))+" "+distUnit(v)+" current recorded odometer");
        List<GarageDb.Service> services=db.services(v.id);
        if(services.isEmpty())noRecords("No maintenance reminders","Schedule oil changes, tire rotations, inspections or registrations.");
        for(GarageDb.Service service:services){
            int priority=service.priority(System.currentTimeMillis(),odo);
            String flag=priority==3?"COMPLETED":priority==0?"OVERDUE":priority==1?"DUE SOON":"SCHEDULED";
            String criteria=(service.dueDate>0?"By "+iso(service.dueDate):"")
                +(service.dueKm>0?(service.dueDate>0?" · ":"")+fmt(service.dueKm/unitFactor(v))+" "+distUnit(v):"");
            LinearLayout card=record(flag+"  ·  "+service.title,criteria+(service.note.isEmpty()?"":"  ·  "+service.note));
            card.setOnClickListener(w->serviceDialog(v,service));
            card.setOnLongClickListener(w->{
                new AlertDialog.Builder(this).setTitle(service.title).setItems(new String[]{service.completed?"Mark as pending":"Mark completed","Edit reminder","Delete"},(dialog,which)->{
                    if(which==0){db.saveService(v.id,service.id,service.title,service.dueDate,service.dueKm,service.note,!service.completed);render();}
                    if(which==1)serviceDialog(v,service);
                    if(which==2)confirm("Delete reminder?","This reminder will be removed.",()->db.deleteService(v.id,service.id));
                }).show();return true;
            });
            body.addView(card,Ui.below(this,10));
        }
        note("Tap to edit · long-press to complete, reopen or delete. Due status is calculated on-device; notifications are not enabled yet.");
    }
    private void serviceDialog(GarageDb.Vehicle v,GarageDb.Service existing){
        LinearLayout col=Ui.col(this);
        EditText title=field(col,"SERVICE TITLE","Oil change",false);
        EditText date=field(col,"DUE DATE (YYYY-MM-DD, OPTIONAL)","2027-01-15",false);
        EditText odo=field(col,"DUE ODOMETER ("+distUnit(v)+", OPTIONAL)","45000",true);
        EditText note=field(col,"NOTES","Oil type, mechanic, etc.",false);
        CheckBox done=new CheckBox(this);done.setText("Completed");done.setTextColor(Ui.INK);col.addView(done,Ui.below(this,10));
        if(existing!=null){
            title.setText(existing.title);date.setText(existing.dueDate>0?iso(existing.dueDate):"");
            odo.setText(existing.dueKm>0?Double.toString(existing.dueKm/unitFactor(v)):"");
            note.setText(existing.note);done.setChecked(existing.completed);
        }
        form(existing==null?"New service reminder":"Edit service reminder",col,()->{
            String s=date.getText().toString().trim();
            long dateMs=s.isEmpty()?0:editorDate(date,existing==null?0:existing.dueDate);
            String mileage=odo.getText().toString().trim();
            double dueKm=mileage.isEmpty()?0:val(odo,"due odometer")*unitFactor(v);
            db.saveService(v.id,existing==null?0:existing.id,title.getText().toString(),dateMs,dueKm,note.getText().toString(),done.isChecked());
        });
    }
    private void stationSection(GarageDb.Vehicle v){
        body.addView(Ui.label(this,"SAVED FUEL STATIONS",13,Ui.MUTED,true),Ui.below(this,25));
        android.widget.Button add=Ui.secondary(this,"＋  Add fuel station");add.setOnClickListener(w->stationDialog(v,null));body.addView(add,Ui.below(this,9));
        for(GarageDb.Station station:db.stations(v.id)){
            LinearLayout card=record(station.name,station.address.isEmpty()?"Tap to edit":station.address);
            card.setOnClickListener(w->stationDialog(v,station));
            card.setOnLongClickListener(w->{confirm("Delete station?","Existing fill-ups will keep their amounts but lose the station reference.",()->db.deleteStation(v.id,station.id));return true;});
            body.addView(card,Ui.below(this,8));
        }
    }
    private void stationDialog(GarageDb.Vehicle v,GarageDb.Station existing){
        LinearLayout col=Ui.col(this);
        EditText name=field(col,"STATION NAME","Shell",false);
        EditText address=field(col,"ADDRESS / LOCATION","Optional",false);
        if(existing!=null){name.setText(existing.name);address.setText(existing.address);}
        form(existing==null?"Save fuel station":"Edit fuel station",col,()->db.saveStation(v.id,existing==null?0:existing.id,name.getText().toString(),address.getText().toString()));
    }
    private void categorySection(GarageDb.Vehicle v){
        body.addView(Ui.label(this,"CUSTOM EXPENSE CATEGORIES",13,Ui.MUTED,true),Ui.below(this,25));
        android.widget.Button add=Ui.secondary(this,"＋  Add custom category");add.setOnClickListener(w->categoryDialog(v));body.addView(add,Ui.below(this,9));
        for(String category:db.customCategories(v.id)){
            LinearLayout card=record(category,"Long-press to remove from suggestions");
            card.setOnLongClickListener(w->{confirm("Remove category?","Existing expenses retain their category labels.",()->db.deleteCategory(v.id,category));return true;});
            body.addView(card,Ui.below(this,8));
        }
    }
    private void categoryDialog(GarageDb.Vehicle v){
        LinearLayout col=Ui.col(this);
        EditText name=field(col,"CATEGORY NAME","Car wash",false);
        form("Add expense category",col,()->db.addCategory(v.id,name.getText().toString()));
    }
    private void fuelScreen(GarageDb.Vehicle v){
        section("Fill-ups","Track every tank, full or partial.");
        addButton("＋  New fill-up",()->fuelDialog(v));
        GarageDb.Economy e=db.economy(v.id);
        summaryNote(Double.isNaN(e.litersPer100Km)?"Fuel economy pending":economyValue(v,e,1)+" "+economyLabel(v,1),
            e.intervalValues.isEmpty()?"Add two full-tank fill-ups with increasing odometer readings.":"Weighted across "+e.intervalValues.size()+" completed full-to-full intervals.");
        List<GarageDb.Fuel> entries=db.fuel(v.id);
        int secondary=0;for(GarageDb.Fuel f:entries)if(f.tankNumber==2)secondary++;
        if(secondary>0){
            GarageDb.Economy second=db.economy(v.id,2);
            summaryNote(Double.isNaN(second.litersPer100Km)?"Tank 2 economy pending":"Tank 2: "+economyValue(v,second,2)+" "+economyLabel(v,2),
                secondary+" tank 2 fill-ups · "+second.intervalValues.size()+" complete intervals");
        }
        if(entries.isEmpty()) noRecords("No fill-ups yet","Your first entry is one tap away.");
        for(GarageDb.Fuel f:entries) fuelRecord(f,v);
        note("Tap to edit a fill-up · long-press to delete.");
    }
    private void fuelRecord(GarageDb.Fuel f,GarageDb.Vehicle v) {
        LinearLayout c=record("⛽  "+fmt(FuelUnits.fromBase(f.liters,v.tankUnit(f.tankNumber)))+" "+v.tankUnit(f.tankNumber)+"  ·  "+usd(f.cost),
            day(f.date)+" "+new SimpleDateFormat("HH:mm",Locale.US).format(new Date(f.date))+"   ·   "+fmt(f.odometer/unitFactor(v))+" "+distUnit(v)
            +"   ·   Tank "+f.tankNumber+" · "+(f.full?"Full tank":"Partial fill-up")+(f.missed?" · Missed entry":"")+(f.stationId>0?" · "+db.stationName(f.stationId):"")+(f.discount>0?" · Saved "+usd(f.discount):"")+(f.fuelSubtype.isEmpty()?"":" · "+f.fuelSubtype));
        c.setOnClickListener(view->fuelDialog(v,f));
        c.setOnLongClickListener(view->{confirm("Delete fill-up?","This record will be permanently removed.",()->db.deleteFuel(f.id));return true;});
        body.addView(c,Ui.below(this,10));
    }
    private void expenseScreen(GarageDb.Vehicle v){
        section("Expenses","Maintenance, repairs and costs in one place.");
        addButton("＋  New expense",()->expenseDialog(v));
        List<GarageDb.Expense> entries=db.expenses(v.id);
        double sum=0;for(GarageDb.Expense e:entries) sum+=e.amount;
        summaryNote(usd(sum)+" total","Across "+entries.size()+" non-fuel expenses.");
        android.widget.Button manage=Ui.secondary(this,"Manage custom categories");
        manage.setOnClickListener(w->categoryDialog(v));
        body.addView(manage,Ui.below(this,10));
        if(entries.isEmpty()) noRecords("No expenses yet","Log a service, repair, parking charge or any other cost.");
        for(GarageDb.Expense e:entries) {
            LinearLayout c=record(e.category+"  ·  "+usd(e.amount),
                day(e.date)+"   ·   "+(e.odometer>0?fmt(e.odometer/unitFactor(v))+" "+distUnit(v)+"   ·   ":"")+e.note);
            c.setOnClickListener(w->expenseDialog(v,e));
            c.setOnLongClickListener(w->{confirm("Delete expense?","This record will be permanently removed.",()->db.deleteExpense(e.id));return true;});
            body.addView(c,Ui.below(this,10));
        }
        note("Tap to edit an expense · long-press to delete.");
    }
    private void tripScreen(GarageDb.Vehicle v){
        section("Trip log","Manual odometer-based trips, no GPS permission.");
        addButton("＋  Record a trip",()->tripDialog(v));
        List<GarageDb.Trip> entries=db.trips(v.id);
        double total=0;for(GarageDb.Trip t:entries) total+=t.endKm-t.startKm;
        summaryNote(fmt(total/unitFactor(v))+" "+distUnit(v)+" logged",entries.size()+" recorded trips.");
        if(entries.isEmpty()) noRecords("No trips yet","Enter start and end odometer readings to record distance.");
        for(GarageDb.Trip t:entries) {
            LinearLayout c=record("↗  "+fmt((t.endKm-t.startKm)/unitFactor(v))+" "+distUnit(v),day(t.date)+"   ·   "+fmt(t.startKm/unitFactor(v))+" → "+fmt(t.endKm/unitFactor(v))+" "+distUnit(v)+(t.note.isEmpty()?"":"   ·   "+t.note));
            c.setOnClickListener(w->tripDialog(v,t));
            c.setOnLongClickListener(w->{confirm("Delete trip?","This record will be permanently removed.",()->db.deleteTrip(t.id));return true;});
            body.addView(c,Ui.below(this,10));
        }
        note("Tap a trip to edit · long-press to delete. No GPS is collected.");
    }
    private static final class ActivityRecord {
        final long when;final String heading,detail;final Runnable open;
        ActivityRecord(long time,String heading,String detail,Runnable open){
            this.when=time;this.heading=heading;this.detail=detail;this.open=open;
        }
    }
    private void timelineScreen(GarageDb.Vehicle v){
        section("Activity timeline","Fuel, expenses and trips in chronological order, grouped by month.");
        List<ActivityRecord> records=new ArrayList<>();
        for(GarageDb.Fuel f:db.fuel(v.id))records.add(new ActivityRecord(f.date,"Fuel · Tank "+f.tankNumber+" · "+usd(f.cost),
            fmt(FuelUnits.fromBase(f.liters,v.tankUnit(f.tankNumber)))+" "+v.tankUnit(f.tankNumber)+" · "+fmt(f.odometer/unitFactor(v))+" "+distUnit(v),()->fuelDialog(v,f)));
        for(GarageDb.Expense e:db.expenses(v.id))records.add(new ActivityRecord(e.date,e.category+" · "+usd(e.amount),e.note,()->expenseDialog(v,e)));
        for(GarageDb.Trip t:db.trips(v.id))records.add(new ActivityRecord(t.date,"Trip · "+fmt((t.endKm-t.startKm)/unitFactor(v))+" "+distUnit(v),t.note,()->tripDialog(v,t)));
        records.sort((a,b)->Long.compare(b.when,a.when));
        if(records.isEmpty())noRecords("No activity yet","Add a fill-up, expense or trip to build your timeline.");
        String lastMonth="";
        for(ActivityRecord item:records){
            String month=new SimpleDateFormat("MMMM yyyy",Locale.US).format(new Date(item.when));
            if(!month.equals(lastMonth)){
                body.addView(Ui.label(this,month.toUpperCase(Locale.US),12,Ui.MUTED,true),Ui.below(this,20));
                lastMonth=month;
            }
            LinearLayout row=record(item.heading,day(item.when)+" · "+item.detail);
            row.setOnClickListener(w->item.open.run());
            body.addView(row,Ui.below(this,9));
        }
    }
    private void garageScreen(GarageDb.Vehicle selected){
        section("Your garage","Every vehicle in one private place.");
        addButton("＋  Add another vehicle",this::vehicleDialog);
        android.widget.Button editVehicle=Ui.secondary(this,"✎  Edit selected vehicle");
        editVehicle.setOnClickListener(w->vehicleDialog(selected));
        body.addView(editVehicle,Ui.below(this,10));
        android.widget.Button prefs=Ui.secondary(this,"⚙  Vehicle details, fuel and units");
        prefs.setOnClickListener(w->vehiclePreferencesDialog(selected));
        body.addView(prefs,Ui.below(this,10));
        android.widget.Button importButton=Ui.secondary(this,"↓  Import from Fuelio CSV / Google Drive");
        importButton.setOnClickListener(w->importData());
        body.addView(importButton,Ui.below(this,12));
        for(GarageDb.Vehicle v:db.vehicles()) {
            LinearLayout card=record((v.id==selectedVehicle?"●  ":"○  ")+v.name,join(v.make,v.model)+"  ·  tap to select");
            card.setOnClickListener(w->{selectedVehicle=v.id;render();});
            card.setOnLongClickListener(w->{
                confirm("Delete "+v.name+"?","This permanently deletes the vehicle AND its fill-ups, expenses and trips.",()->{
                    db.deleteVehicle(v.id);
                    if(selectedVehicle==v.id)selectedVehicle=-1;
                });return true;
            });
            body.addView(card,Ui.below(this,12));
        }
        android.widget.Button export=Ui.secondary(this,"↓  Export summary CSV (not a restore backup)");
        export.setOnClickListener(w->exportData());
        body.addView(export,Ui.below(this,25));
        android.widget.Button backup=Ui.secondary(this,"⤓  Back up all RoadLedger data (.zip)");
        backup.setOnClickListener(w->createBackup());
        body.addView(backup,Ui.below(this,12));
        android.widget.Button restore=Ui.secondary(this,"⤒  Restore RoadLedger backup (.zip)");
        restore.setOnClickListener(w->chooseRestore());
        body.addView(restore,Ui.below(this,12));
        stationSection(selected);
        categorySection(selected);
        if(db.archivedFuelioFiles()>0){
            android.widget.Button original=Ui.secondary(this,"↓  Export preserved original Fuelio CSV");
            original.setOnClickListener(w->chooseOriginalExport());
            body.addView(original,Ui.below(this,12));
        }
        note("Raw source preservation: "+db.archivedFuelioFiles()+" original Fuelio CSV export(s) safely archived offline. Full source bytes are included in your RoadLedger backup.");
        note("Native ZIP backup includes fuel tanks, original imports, stations, categories and service schedules. It is NOT encrypted; store it privately.");
        note("Tap a vehicle to select. Edit above or long-press to delete it and its data.");
    }

    private void section(String title,String subtitle) {
        body.addView(Ui.label(this,title,27,Ui.INK,true));
        body.addView(Ui.label(this,subtitle,13,Ui.MUTED,false),Ui.below(this,5));
    }
    private void note(String text) {
        TextView t=Ui.label(this,text,11,Ui.MUTED,false);
        t.setGravity(Gravity.CENTER);body.addView(t,Ui.below(this,21));
    }
    private void noRecords(String title,String explanation) {
        LinearLayout card=Ui.card(this);
        card.addView(Ui.label(this,title,17,Ui.INK,true));
        card.addView(Ui.label(this,explanation,13,Ui.MUTED,false),Ui.below(this,6));
        body.addView(card,Ui.below(this,16));
    }
    private void summaryNote(String title,String explanation) {
        LinearLayout card=Ui.card(this);
        card.setBackground(Ui.shape(Ui.CARD_ALT,this,21));
        card.addView(Ui.label(this,title,21,Ui.MINT,true));
        card.addView(Ui.label(this,explanation,12,Ui.MUTED,false),Ui.below(this,4));
        body.addView(card,Ui.below(this,18));
    }
    private LinearLayout metric(String heading,String value,String detail,int color) {
        LinearLayout c=Ui.card(this);
        c.setPadding(Ui.dp(this,13),Ui.dp(this,18),Ui.dp(this,10),Ui.dp(this,18));
        c.addView(Ui.label(this,heading,10,Ui.MUTED,true));
        c.addView(Ui.label(this,value,22,color,true),Ui.below(this,9));
        c.addView(Ui.label(this,detail,11,Ui.MUTED,false),Ui.below(this,5));
        return c;
    }
    private LinearLayout.LayoutParams weighted() {return new LinearLayout.LayoutParams(0,-2,1);}
    private LinearLayout.LayoutParams weighted(int leftMargin) {
        LinearLayout.LayoutParams p=weighted();p.leftMargin=Ui.dp(this,leftMargin);return p;
    }
    private LinearLayout record(String headline,String sub) {
        LinearLayout c=Ui.card(this);
        c.addView(Ui.label(this,headline,16,Ui.INK,true));
        c.addView(Ui.label(this,sub,12,Ui.MUTED,false),Ui.below(this,8));
        return c;
    }
    private void addButton(String title,Runnable r) {
        android.widget.Button b=Ui.primary(this,title);
        b.setOnClickListener(v->r.run());
        body.addView(b,Ui.below(this,18));
    }
    private void chooseVehicle(){
        List<GarageDb.Vehicle> vehicles=db.vehicles();
        if(vehicles.isEmpty()) {vehicleDialog();return;}
        String[] items=new String[vehicles.size()+1];
        for(int i=0;i<vehicles.size();i++)items[i]=vehicles.get(i).name+"  ·  "+join(vehicles.get(i).make,vehicles.get(i).model);
        items[vehicles.size()]="+ Add another vehicle";
        new AlertDialog.Builder(this).setTitle("Select vehicle").setItems(items,(dialog,which)->{
            if(which==vehicles.size())vehicleDialog();
            else{selectedVehicle=vehicles.get(which).id;render();}
        }).show();
    }
    private interface SaveAction {void save() throws Exception;}
    private void form(String title,LinearLayout fields,SaveAction action) {
        LinearLayout wrap=Ui.col(this);
        wrap.setPadding(Ui.dp(this,20),Ui.dp(this,8),Ui.dp(this,20),0);
        wrap.addView(fields);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(wrap)
            .setPositiveButton("Save",null).setNegativeButton("Cancel",(d,w)->{}).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {action.save();dialog.dismiss();render();}
            catch(Exception ex){toast(ex instanceof NumberFormatException?"Enter valid numeric values.":(ex.getMessage()==null?"Invalid input.":ex.getMessage()));}
        }));
        dialog.show();
    }
    private EditText field(LinearLayout parent,String label,String placeholder,boolean number) {
        TextView t=Ui.label(this,label,13,Color.DKGRAY,true);
        parent.addView(t,Ui.below(this,12));
        EditText e=new EditText(this);
        e.setSingleLine(true);e.setTextSize(16);e.setHint(placeholder);
        e.setTextColor(Ui.INK);e.setHintTextColor(Ui.MUTED);
        if(number) e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        else e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        parent.addView(e,new LinearLayout.LayoutParams(-1,Ui.dp(this,50)));
        return e;
    }
    private static double val(EditText e,String field) {
        String text=e.getText().toString().trim();
        if(text.isEmpty()) throw new IllegalArgumentException("Enter "+field+".");
        double n=Double.parseDouble(text);
        if(!Double.isFinite(n))throw new IllegalArgumentException("Invalid "+field+".");
        return n;
    }
    private static int indexOf(String[] items,String value){
        for(int i=0;i<items.length;i++)if(items[i].equals(value))return i;
        return 0;
    }
    private Spinner option(LinearLayout parent,String title,String[] choices,String selected){
        parent.addView(Ui.label(this,title,13,Ui.MUTED,true),Ui.below(this,12));
        Spinner spinner=new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));
        spinner.setSelection(indexOf(choices,selected));
        parent.addView(spinner,Ui.below(this,4));
        return spinner;
    }
    private void linkFuelUnits(Spinner type,Spinner unit,String preferred){
        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            private boolean initial=true;
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                String[] units=FuelUnits.unitsFor((String)type.getSelectedItem());
                String value=initial?preferred:(String)unit.getSelectedItem();
                unit.setAdapter(new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,units));
                unit.setSelection(indexOf(units,value));
                initial=false;
            }
        });
    }
    private void watchCapacityUnit(Spinner unit,EditText capacity,String initial){
        unit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            private String previous=initial;
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int pos,long id){
                String next=(String)unit.getSelectedItem();
                if(previous.equals(next))return;
                String input=capacity.getText().toString().trim();
                if(!input.isEmpty()){
                    try{
                        double n=Double.parseDouble(input);
                        if(FuelUnits.baseUnit(previous).equals(FuelUnits.baseUnit(next)))
                            capacity.setText(Double.toString(n*FuelUnits.factor(previous)/FuelUnits.factor(next)));
                        else capacity.setText("");
                    }catch(Exception invalid){capacity.setText("");}
                }
                previous=next;
            }
        });
    }
    private static double optionalNonnegative(EditText field,String label){
        String value=field.getText().toString().trim();
        if(value.isEmpty())return 0;
        double number=Double.parseDouble(value);
        if(!Double.isFinite(number)||number<0)throw new IllegalArgumentException("Invalid "+label+".");
        return number;
    }
    private void vehiclePreferencesDialog(GarageDb.Vehicle vehicle){
        LinearLayout fields=Ui.col(this);
        EditText year=field(fields,"MODEL YEAR (OPTIONAL)","2022",true);
        EditText vin=field(fields,"VIN (OPTIONAL)","Vehicle identification number",false);
        EditText registration=field(fields,"REGISTRATION / PLATE (OPTIONAL)","Plate",false);
        EditText insurance=field(fields,"INSURANCE (OPTIONAL)","Provider or policy",false);
        Spinner distance=option(fields,"DISTANCE",FuelUnits.DISTANCES,vehicle.unitSystem==1?"Miles":"Kilometres");
        Spinner fuel1=option(fields,"PRIMARY FUEL TYPE",FuelUnits.TYPES,vehicle.tank1Fuel);
        Spinner unit1=option(fields,"PRIMARY QUANTITY UNIT",FuelUnits.unitsFor(vehicle.tank1Fuel),vehicle.tank1Unit);
        linkFuelUnits(fuel1,unit1,vehicle.tank1Unit);
        EditText capacity1=field(fields,"PRIMARY TANK CAPACITY (OPTIONAL, IN SELECTED UNIT)","e.g. 40",true);
        Spinner fuel2=option(fields,"SECOND TANK / BI-FUEL",FuelUnits.TYPES,vehicle.tank2Fuel);
        Spinner unit2=option(fields,"SECONDARY QUANTITY UNIT",FuelUnits.unitsFor(vehicle.tank2Fuel),vehicle.tank2Unit);
        linkFuelUnits(fuel2,unit2,vehicle.tank2Unit);
        EditText capacity2=field(fields,"SECONDARY TANK CAPACITY (OPTIONAL, IN SELECTED UNIT)","e.g. 10",true);
        Spinner economy=option(fields,"FUEL ECONOMY DISPLAY",FuelUnits.ECONOMY,vehicle.consumptionUnit);
        year.setText(vehicle.year==0?"":Integer.toString(vehicle.year));vin.setText(vehicle.vin);
        registration.setText(vehicle.registration);insurance.setText(vehicle.insurance);
        capacity1.setText(vehicle.tank1Capacity==0?"":Double.toString(FuelUnits.fromBase(vehicle.tank1Capacity,vehicle.tank1Unit)));
        capacity2.setText(vehicle.tank2Capacity==0?"":Double.toString(FuelUnits.fromBase(vehicle.tank2Capacity,vehicle.tank2Unit)));
        watchCapacityUnit(unit1,capacity1,vehicle.tank1Unit);
        watchCapacityUnit(unit2,capacity2,vehicle.tank2Unit);
        ScrollView scroller=new ScrollView(this);
        scroller.addView(fields);
        LinearLayout scrollHost=Ui.col(this);
        scrollHost.addView(scroller,new LinearLayout.LayoutParams(-1,
            Math.min(Ui.dp(this,510),(int)(getResources().getDisplayMetrics().heightPixels*.56))));
        form("Vehicle preferences",scrollHost,()->{
            String text=year.getText().toString().trim();
            int numericYear=text.isEmpty()?0:Integer.parseInt(text);
            db.updateVehiclePreferences(vehicle.id,numericYear,vin.getText().toString(),registration.getText().toString(),
                insurance.getText().toString(),distance.getSelectedItemPosition(),(String)fuel1.getSelectedItem(),
                (String)unit1.getSelectedItem(),optionalNonnegative(capacity1,"tank capacity"),
                (String)fuel2.getSelectedItem(),(String)unit2.getSelectedItem(),optionalNonnegative(capacity2,"tank capacity"),
                (String)economy.getSelectedItem());
        });
    }
    private void vehicleDialog() { vehicleDialog(null); }
    private void vehicleDialog(GarageDb.Vehicle existing) {
        LinearLayout col=Ui.col(this);
        EditText name=field(col,"VEHICLE NAME","e.g. My Corolla",false);
        EditText make=field(col,"MAKE","Toyota (optional)",false);
        EditText model=field(col,"MODEL","Corolla (optional)",false);
        if(existing!=null){name.setText(existing.name);make.setText(existing.make);model.setText(existing.model);}
        form(existing==null?"Add vehicle":"Edit vehicle",col,()->{
            String label=name.getText().toString().trim();
            if(label.isEmpty())throw new IllegalArgumentException("Vehicle name is required.");
            if(existing==null)selectedVehicle=db.addVehicle(label,make.getText().toString(),model.getText().toString());
            else db.updateVehicle(existing.id,label,make.getText().toString(),model.getText().toString());
            tab=0;
        });
    }
    private void fuelDialog(GarageDb.Vehicle vehicle){fuelDialog(vehicle,null);}
    private void fuelDialog(GarageDb.Vehicle vehicle,GarageDb.Fuel existing) {
        LinearLayout col=Ui.col(this);
        EditText date=field(col,"DATE (YYYY-MM-DD)","2026-11-11",false);
        EditText time=field(col,"TIME (HH:MM)","18:30",false);
        EditText odo=field(col,"ODOMETER ("+distUnit(vehicle).toUpperCase(Locale.US)+")","e.g. 37000",true);
        EditText quantity=field(col,"FUEL / ENERGY QUANTITY","e.g. 8.5",true);
        EditText price=field(col,"TOTAL PRICE BEFORE DISCOUNT","e.g. 47.50",true);
        EditText discount=field(col,"DISCOUNT (OPTIONAL)","e.g. 2.00",true);
        CheckBox full=new CheckBox(this);full.setText("Filled tank completely");
        full.setChecked(existing==null||existing.full);full.setTextColor(Ui.INK);
        col.addView(full,Ui.below(this,15));
        CheckBox missed=new CheckBox(this);missed.setText("Prior fill-up history is missing");
        missed.setChecked(existing!=null&&existing.missed);missed.setTextColor(Ui.INK);
        col.addView(missed,Ui.below(this,10));
        col.addView(Ui.label(this,"TANK",13,Ui.MUTED,true),Ui.below(this,12));
        String[] tanks="Disabled".equals(vehicle.tank2Fuel)?new String[]{"Tank 1 · "+vehicle.tank1Fuel}:
            new String[]{"Tank 1 · "+vehicle.tank1Fuel,"Tank 2 · "+vehicle.tank2Fuel};
        Spinner tank=new Spinner(this);
        tank.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,tanks));
        tank.setSelection(existing!=null&&existing.tankNumber==2&&tanks.length==2?1:0);
        col.addView(tank,Ui.below(this,4));
        col.addView(Ui.label(this,"QUANTITY UNIT",13,Ui.MUTED,true),Ui.below(this,12));
        Spinner unit=new Spinner(this);col.addView(unit,Ui.below(this,4));
        int selectedTank=(existing!=null&&existing.tankNumber==2&&tanks.length==2)?2:1;
        String preferred=existing==null?vehicle.tankUnit(selectedTank):existing.sourceUnit;
        if(existing!=null&&"L".equals(preferred)&&vehicle.unitSystem==1&&
                "L".equals(FuelUnits.baseUnit(vehicle.tankUnit(selectedTank))))preferred=vehicle.tankUnit(selectedTank);
        if(!FuelUnits.contains(FuelUnits.unitsFor(vehicle.tankFuel(selectedTank)),preferred))
            preferred=vehicle.tankUnit(selectedTank);
        String[] available=FuelUnits.unitsFor(vehicle.tankFuel(selectedTank));
        unit.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,available));
        unit.setSelection(indexOf(available,preferred));
        final int[] lastTank={selectedTank};
        tank.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long id){
                int chosen=position+1;
                if(chosen==lastTank[0])return;
                lastTank[0]=chosen;
                String[] units=FuelUnits.unitsFor(vehicle.tankFuel(chosen));
                unit.setAdapter(new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,units));
                unit.setSelection(indexOf(units,vehicle.tankUnit(chosen)));
                quantity.setText(""); // Tank switch requires new quantity; never silently reinterpret units.
            }
        });
        List<GarageDb.Station> stations=db.stations(vehicle.id);
        String[] names=new String[stations.size()+1];names[0]="Unspecified station";
        int activeStation=0;
        for(int i=0;i<stations.size();i++){
            names[i+1]=stations.get(i).name;
            if(existing!=null&&stations.get(i).id==existing.stationId)activeStation=i+1;
        }
        Spinner station=option(col,"FUEL STATION",names,names[0]);
        station.setSelection(activeStation);
        EditText subtype=field(col,"FUEL SUBTYPE (OPTIONAL)","e.g. E85, premium",false);
        EditText weather=field(col,"WEATHER (OPTIONAL, MANUAL)","e.g. Rain",false);
        long timestamp=existing==null?System.currentTimeMillis():existing.date;
        date.setText(iso(timestamp));
        time.setText(new SimpleDateFormat("HH:mm",Locale.US).format(new Date(timestamp)));
        if(existing!=null){
            odo.setText(Double.toString(existing.odometer/unitFactor(vehicle)));
            quantity.setText(Double.toString(FuelUnits.fromBase(existing.liters,preferred)));
            price.setText(Double.toString(existing.cost+existing.discount));
            discount.setText(existing.discount==0?"":Double.toString(existing.discount));
            subtype.setText(existing.fuelSubtype);weather.setText(existing.weather);
        }
        watchCapacityUnit(unit,quantity,preferred);
        form(existing==null?"New fill-up":"Edit fill-up",col,()->{
            long dateMs=editorDateTime(date,time,existing==null?0:existing.date);
            double distance=val(odo,"odometer")*unitFactor(vehicle);
            String unitChoice=(String)unit.getSelectedItem();
            double amount=FuelUnits.toBase(val(quantity,"quantity"),unitChoice);
            double gross=val(price,"total price");
            double reduction=optionalNonnegative(discount,"discount");
            if(reduction>gross)throw new IllegalArgumentException("Discount cannot exceed total price.");
            long stationId=station.getSelectedItemPosition()==0?0:stations.get(station.getSelectedItemPosition()-1).id;
            db.saveFuelEntry(vehicle.id,existing==null?0:existing.id,dateMs,distance,amount,gross-reduction,
                full.isChecked(),missed.isChecked(),tank.getSelectedItemPosition()+1,stationId,unitChoice,
                subtype.getText().toString(),reduction,weather.getText().toString());
        });
    }
    private void expenseDialog(GarageDb.Vehicle vehicle){expenseDialog(vehicle,null);}
    private void expenseDialog(GarageDb.Vehicle vehicle,GarageDb.Expense existing) {
        LinearLayout col=Ui.col(this);
        EditText date=field(col,"DATE (YYYY-MM-DD)","2026-11-10",false);
        col.addView(Ui.label(this,"CATEGORY",13,Ui.MUTED,true),Ui.below(this,12));
        Spinner category=new Spinner(this);
        String[] options=db.categoryOptions(vehicle.id);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        category.setAdapter(adapter);col.addView(category,Ui.below(this,4));
        EditText amount=field(col,"AMOUNT (USD)","e.g. 89.50",true);
        EditText odo=field(col,"ODOMETER ("+distUnit(vehicle).toUpperCase(Locale.US)+", OPTIONAL)","0",true);
        EditText note=field(col,"NOTES","What was this for?",false);
        date.setText(iso(existing==null?System.currentTimeMillis():existing.date));
        if(existing!=null){
            amount.setText(Double.toString(existing.amount));
            odo.setText(Double.toString(existing.odometer/unitFactor(vehicle)));
            note.setText(existing.note);
            // Preserve imported/custom categories instead of silently replacing them.
            boolean found=false;
            for(int i=0;i<options.length;i++)if(options[i].equals(existing.category)){category.setSelection(i);found=true;break;}
            if(!found){
                String[] extended=new String[options.length+1];
                extended[0]=existing.category;
                System.arraycopy(options,0,extended,1,options.length);
                options=extended;
                category.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));
            }
        }
        final String[] categories=options;
        form(existing==null?"New expense":"Edit expense",col,()->{
            String km=odo.getText().toString().trim();
            double mileage=km.isEmpty()?0:Double.parseDouble(km)*unitFactor(vehicle);
            long dateMs=editorDate(date,existing==null?0:existing.date);
            String selected=categories[category.getSelectedItemPosition()];
            if(existing==null) db.addExpense(vehicle.id,dateMs,selected,val(amount,"amount"),mileage,note.getText().toString());
            else db.updateExpense(vehicle.id,existing.id,dateMs,selected,val(amount,"amount"),mileage,note.getText().toString());
        });
    }
    private void tripDialog(GarageDb.Vehicle vehicle){tripDialog(vehicle,null);}
    private void tripDialog(GarageDb.Vehicle vehicle,GarageDb.Trip existing) {
        LinearLayout col=Ui.col(this);
        EditText date=field(col,"DATE (YYYY-MM-DD)","2026-11-10",false);
        EditText start=field(col,"START ODOMETER ("+distUnit(vehicle).toUpperCase(Locale.US)+")","e.g. 37000",true);
        EditText end=field(col,"END ODOMETER ("+distUnit(vehicle).toUpperCase(Locale.US)+")","e.g. 37025",true);
        EditText note=field(col,"TRIP PURPOSE / NOTES","e.g. Commute",false);
        date.setText(iso(existing==null?System.currentTimeMillis():existing.date));
        if(existing!=null){
            start.setText(Double.toString(existing.startKm/unitFactor(vehicle)));
            end.setText(Double.toString(existing.endKm/unitFactor(vehicle)));
            note.setText(existing.note);
        }
        form(existing==null?"Record manual trip":"Edit trip",col,()->{
            long dateMs=editorDate(date,existing==null?0:existing.date);
            double startKm=val(start,"starting odometer")*unitFactor(vehicle);
            double endKm=val(end,"ending odometer")*unitFactor(vehicle);
            if(existing==null)db.addTrip(vehicle.id,dateMs,startKm,endKm,note.getText().toString());
            else db.updateTrip(vehicle.id,existing.id,dateMs,startKm,endKm,note.getText().toString());
        });
    }
    private static long editorDateTime(EditText date,EditText time,long original) throws Exception {
        String input=date.getText().toString().trim()+" "+time.getText().toString().trim();
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);
        format.setLenient(false);
        java.text.ParsePosition pos=new java.text.ParsePosition(0);
        Date parsed=format.parse(input,pos);
        if(parsed==null||pos.getIndex()!=input.length()||input.length()!=16)
            throw new IllegalArgumentException("Use YYYY-MM-DD and 24-hour HH:MM for fill-up date/time.");
        if(original>0&&input.equals(format.format(new Date(original))))return original;
        return parsed.getTime();
    }
    private static long editorDate(EditText field,long original) throws Exception {
        String value=field.getText().toString().trim();
        SimpleDateFormat formatter=new SimpleDateFormat("yyyy-MM-dd",Locale.US);
        formatter.setLenient(false);
        java.text.ParsePosition position=new java.text.ParsePosition(0);
        Date parsed=formatter.parse(value,position);
        if(parsed==null||position.getIndex()!=value.length()||value.length()!=10)
            throw new IllegalArgumentException("Enter a valid date as YYYY-MM-DD.");
        // A record whose date was not changed must keep its original time-of-day.
        if(original>0&&value.equals(iso(original)))return original;
        return parsed.getTime();
    }
    private void confirm(String title,String message,Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton("Delete",(d,w)->{action.run();render();})
            .setNegativeButton("Cancel",null).show();
    }
    private static double unitFactor(GarageDb.Vehicle v){return v.unitSystem==1?KM_PER_MILE:1;}
    private static double fuelFactor(GarageDb.Vehicle v){return v.unitSystem==1?LITERS_PER_GALLON:1;}
    private static String distUnit(GarageDb.Vehicle v){return v.unitSystem==1?"mi":"km";}
    private static String fuelUnit(GarageDb.Vehicle v){return v.unitSystem==1?"gal":"L";}
    private static String economyLabel(GarageDb.Vehicle v,int tank){
        return FuelUnits.consumptionUnit(FuelUnits.baseUnit(v.tankUnit(tank)),v.consumptionUnit,v.unitSystem);
    }
    private static String economyValue(GarageDb.Vehicle v,GarageDb.Economy e,int tank){
        return fmt(FuelUnits.displayConsumption(e.litersPer100Km,FuelUnits.baseUnit(v.tankUnit(tank)),v.consumptionUnit,v.unitSystem));
    }
    private void importData(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {startActivityForResult(intent,IMPORT_REQUEST);}
        catch(Exception e){toast("Could not open the file picker.");}
    }
    private void showImportPreview(FuelioImport.Preview preview){
        new AlertDialog.Builder(this).setTitle("Import Fuelio history?")
            .setMessage(preview.describe())
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Import", (dialog,which)->{
                try {
                    FuelioImport.Outcome outcome=FuelioImport.apply(db,preview);
                    if(outcome.lastVehicleId>0)selectedVehicle=outcome.lastVehicleId;
                    tab=0;render();
                    new AlertDialog.Builder(this).setTitle("Import completed")
                        .setMessage(outcome.summary()).setPositiveButton("Done",null).show();
                }catch(Exception e){
                    new AlertDialog.Builder(this).setTitle("Import failed")
                        .setMessage("No partial changes were saved. "+e.getMessage())
                        .setPositiveButton("OK",null).show();
                }
            }).show();
    }
    private void createBackup(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE,"roadledger-native-backup.zip");
        try{startActivityForResult(i,BACKUP_REQUEST);}
        catch(Exception e){toast("No file picker available.");}
    }
    private void chooseRestore(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        try{startActivityForResult(i,RESTORE_REQUEST);}
        catch(Exception e){toast("No file picker available.");}
    }
    private void confirmRestore(RoadLedgerBackup.Snapshot snapshot){
        new AlertDialog.Builder(this)
            .setTitle("Replace all RoadLedger data?")
            .setMessage(snapshot.summary()+"\n\nExport a backup of your current records first. This action replaces them.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Replace all",(dialog,which)->{
                try{
                    RoadLedgerBackup.restore(db,snapshot);
                    selectedVehicle=-1;tab=0;render();
                    new AlertDialog.Builder(this).setTitle("Restore successful")
                        .setMessage("All backed-up records have been restored.")
                        .setPositiveButton("Done",null).show();
                }catch(Exception e){
                    new AlertDialog.Builder(this).setTitle("Restore failed")
                        .setMessage("Existing data was not replaced. "+e.getMessage())
                        .setPositiveButton("OK",null).show();
                }
            }).show();
    }
    private void chooseOriginalExport(){
        List<GarageDb.ArchivedSource> sources=db.archivedSources();
        if(sources.isEmpty()){toast("No original CSV is archived.");return;}
        String[] labels=new String[sources.size()];
        for(int i=0;i<sources.size();i++){
            labels[i]=sources.get(i).filename+" · "+sources.get(i).sha256.substring(0,8);
        }
        new AlertDialog.Builder(this).setTitle("Export original Fuelio CSV")
            .setItems(labels,(dialog,which)->{
                archiveToExport=sources.get(which).sha256;
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE,"fuelio-preserved-original.csv");
                try{startActivityForResult(intent,SOURCE_EXPORT_REQUEST);}
                catch(Exception e){toast("No document picker available.");}
            }).show();
    }
    private void selectReportPeriod(GarageDb.Vehicle vehicle){
        new AlertDialog.Builder(this).setTitle("Vehicle report period")
            .setItems(new String[]{"All history","Last 12 months"},(dlg,option)->{
                reportVehicleId=vehicle.id;
                if(option==0)reportSince=0;
                else{
                    java.util.Calendar c=java.util.Calendar.getInstance();
                    c.add(java.util.Calendar.MONTH,-12);
                    reportSince=c.getTimeInMillis();
                }
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE,"roadledger-detailed-report.csv");
                try{startActivityForResult(intent,REPORT_REQUEST);}
                catch(Exception e){toast("No report destination available.");}
            }).show();
    }
    private void exportData(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/csv");
        i.putExtra(Intent.EXTRA_TITLE,"roadledger-export.csv");
        try{startActivityForResult(i,EXPORT_REQUEST);}
        catch(Exception e){toast("No document picker found.");}
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==SOURCE_EXPORT_REQUEST){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                byte[] original=RoadLedgerSourceArchive.decodeVerified(db.archivePayloadBase64(archiveToExport),archiveToExport);
                out.write(original);
                toast("Original Fuelio CSV saved without modifying its content.");
            }catch(Exception e){toast("Original CSV export failed: "+e.getMessage());}
            return;
        }
        if(requestCode==REPORT_REQUEST){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                RoadLedgerReport.write(db,reportVehicleId,reportSince,System.currentTimeMillis(),out);
                toast("Vehicle report exported.");
            }catch(Exception e){toast("Report failed: "+e.getMessage());}
            return;
        }
        if(requestCode==BACKUP_REQUEST){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                RoadLedgerBackup.write(db,out);
                toast("RoadLedger native backup saved. Store it privately.");
            }catch(Exception e){toast("Backup failed: "+e.getMessage());}
            return;
        }
        if(requestCode==RESTORE_REQUEST){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                confirmRestore(RoadLedgerBackup.read(in));
            }catch(Exception e){
                new AlertDialog.Builder(this).setTitle("Invalid backup")
                    .setMessage(e.getMessage()).setPositiveButton("OK",null).show();
            }
            return;
        }
        if(requestCode==IMPORT_REQUEST){
            if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                FuelioImport.Preview preview=FuelioImport.inspect(in,"Fuelio backup");
                showImportPreview(preview);
            }catch(Exception e){
                new AlertDialog.Builder(this).setTitle("Unable to read Fuelio backup")
                    .setMessage(e.getMessage()).setPositiveButton("OK",null).show();
            }
            return;
        }
        if(requestCode!=EXPORT_REQUEST||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
            if(out==null)throw new IllegalStateException("Could not open export destination.");
            out.write(buildCsv().getBytes(StandardCharsets.UTF_8));
            toast("CSV exported successfully.");
        }catch(Exception e){toast("Export failed: "+e.getMessage());}
    }
    private String buildCsv(){
        StringBuilder b=new StringBuilder();
        b.append("record_type,vehicle_id,vehicle_name,date,odometer_start_km,odometer_end_km,quantity_base,quantity_base_unit,amount_usd,full_tank,category,note\n");
        for(GarageDb.Vehicle v:db.vehicles()){
            csv(b,"vehicle",v.id,v.name,"","","","","","","","",join(v.make,v.model));
            for(GarageDb.Fuel f:db.fuel(v.id))
                csv(b,"fuel",v.id,v.name,iso(f.date),f.odometer,"",f.liters,FuelUnits.baseUnit(f.sourceUnit),f.cost,f.full?"yes":"no","","");
            for(GarageDb.Expense e:db.expenses(v.id))
                csv(b,"expense",v.id,v.name,iso(e.date),e.odometer,"","","",e.amount,"",e.category,e.note);
            for(GarageDb.Trip t:db.trips(v.id))
                csv(b,"trip",v.id,v.name,iso(t.date),t.startKm,t.endKm,"","","","","",t.note);
        }
        return b.toString();
    }
    private static void csv(StringBuilder b,Object...cells) {
        for(int i=0;i<cells.length;i++){
            if(i>0)b.append(',');
            String s=String.valueOf(cells[i]==null?"":cells[i]);
            if(cells[i] instanceof String && !s.isEmpty() && "=+-@".indexOf(s.charAt(0))>=0)s="'"+s;
            b.append('"').append(s.replace("\"","\"\"")).append('"');
        }
        b.append('\n');
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private static String usd(double v){return String.format(Locale.US,"$%,.2f",v);}
    private static String fmt(double v){return String.format(Locale.US,"%,.1f",v);}
    private static String day(long v){return new SimpleDateFormat("MMM d, yyyy",Locale.US).format(new Date(v));}
    private static String iso(long v){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date(v));}
    private static String join(String a,String b){
        String s=(a+" "+b).trim();
        return s.isEmpty()?"Vehicle":s;
    }
}
