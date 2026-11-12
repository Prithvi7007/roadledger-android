package fun.cadacrew.roadledger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfDocument;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Real v7 launcher: task-centered UI, with original RoadLedger retained for advanced import/restore. */
public final class V7Activity extends Activity {
    private static final int BG=Color.rgb(246,248,252),INK=Color.rgb(18,34,56),BLUE=Color.rgb(40,96,230);
    private static final int GREEN=Color.rgb(11,163,133),MUTED=Color.rgb(98,112,134),WHITE=Color.WHITE;
    private static final int PHOTO=700,PDF=701,CSV=702,GPX=703;
    private GarageDb db;private V7Store store;private long chosen=-1,pendingRoute=0,pendingVehicle=0;
    private String screen="Home";private LinearLayout content;private String pendingKind="Receipt";private long pendingRecordId=0;private String expenseFilter="All";
    private final String[] nav={"Home","Fuel","Trips"};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);db=new GarageDb(this);store=new V7Store(db);
        if(saved!=null){chosen=saved.getLong("chosen",-1);screen=saved.getString("screen","Home");}
        else if(getIntent()!=null&&getIntent().getStringExtra("screen")!=null)screen=getIntent().getStringExtra("screen");
        // Android 15 enforces edge-to-edge; draw in the full window, then explicitly
        // inset the app root. This also avoids double-spacing on earlier Android releases.
        if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if(Build.VERSION.SDK_INT>=29)getWindow().setNavigationBarContrastEnforced(false);
        int flags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        if(Build.VERSION.SDK_INT<30)flags|=View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        V7ReminderWorker.schedule(getApplicationContext());draw();}
    @Override public void onResume(){super.onResume();if(db!=null)draw();}
    @Override public void onSaveInstanceState(Bundle b){b.putLong("chosen",chosen);b.putString("screen",screen);super.onSaveInstanceState(b);}
    @Override public void onDestroy(){if(db!=null)db.close();super.onDestroy();}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    private LinearLayout column(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);return x;}
    private LinearLayout row(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.HORIZONTAL);x.setGravity(Gravity.CENTER_VERTICAL);return x;}
    private LinearLayout.LayoutParams below(int margin){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(margin);return p;}
    private LinearLayout.LayoutParams grow(){return new LinearLayout.LayoutParams(0,-2,1);}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setFontFeatureSettings("kern");if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void space(LinearLayout parent,int dp){View gap=new View(this);parent.addView(gap,new LinearLayout.LayoutParams(1,dp(dp)));}
    private LinearLayout card(LinearLayout dest){LinearLayout panel=column();panel.setPadding(dp(18),dp(18),dp(18),dp(18));panel.setBackground(bg(WHITE,22));dest.addView(panel,below(14));return panel;}
    private Button button(String label,boolean primary,Runnable action){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(primary?WHITE:BLUE);b.setBackground(bg(primary?BLUE:Color.rgb(232,239,255),15));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setOnClickListener(v->guard(action));return b;}
    private void action(LinearLayout parent,String label,boolean primary,Runnable action){parent.addView(button(label,primary,action),below(10));}
    private void guard(Runnable action){try{action.run();}catch(Exception e){toast(e.getMessage()==null?"Could not complete action":e.getMessage());}}
    private void toast(String value){Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    private GarageDb.Vehicle vehicle(){List<GarageDb.Vehicle> all=db.vehicles();for(GarageDb.Vehicle v:all)if(v.id==chosen)return v;chosen=all.isEmpty()?-1:all.get(0).id;return all.isEmpty()?null:all.get(0);}
    private void go(String target){screen=target;draw();}
    private boolean isPrimary(String value){return "Home".equals(value)||"Fuel".equals(value)||"Trips".equals(value);}
    @Override public void onBackPressed(){if(!"Home".equals(screen))go("Home");else super.onBackPressed();}
    private void draw(){
        GarageDb.Vehicle v=vehicle();LinearLayout root=column();root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((view,insets)->{
            int top,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets safe=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                top=safe.top;bottom=safe.bottom;
            } else {
                top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();
            }
            view.setPadding(0,top,0,bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        LinearLayout hdr=row();hdr.setPadding(dp(16),dp(10),dp(16),dp(10));root.addView(hdr);
        if(!isPrimary(screen)){
            TextView back=text("‹",32,BLUE,true);back.setGravity(Gravity.CENTER);back.setContentDescription("Back to Home");
            hdr.addView(back,new LinearLayout.LayoutParams(dp(40),dp(48)));back.setOnClickListener(w->go("Home"));
        }
        LinearLayout brand=column();brand.addView(text("ROADLEDGER",21,INK,true));brand.addView(text("YOUR VEHICLE · v0.7.1",10,MUTED,true));hdr.addView(brand,grow());
        TextView chooser=text(v==null?"＋ CAR":"▾  "+v.name,12,BLUE,true);
        chooser.setMaxWidth(dp(isPrimary(screen)?125:98));chooser.setSingleLine(true);chooser.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chooser.setGravity(Gravity.CENTER);chooser.setContentDescription("Switch vehicle");
        chooser.setPadding(dp(9),dp(10),dp(9),dp(10));chooser.setBackground(bg(Color.rgb(228,236,255),14));
        chooser.setMinimumHeight(dp(48));chooser.setOnClickListener(w->selectVehicle());hdr.addView(chooser);
        TextView more=text("⋯",27,BLUE,true);more.setGravity(Gravity.CENTER);more.setContentDescription("More vehicle tools");
        hdr.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));more.setOnClickListener(w->go("More"));
        ScrollView scrolling=new ScrollView(this);scrolling.setFillViewport(true);scrolling.setVerticalScrollBarEnabled(false);root.addView(scrolling,new LinearLayout.LayoutParams(-1,0,1));
        content=column();content.setPadding(dp(20),dp(3),dp(20),dp(32));scrolling.addView(content);
        if(v==null){welcome();}else switch(screen){
            case "Fuel":fuelPage(v);break;case "Expenses":expensePage(v);break;case "Trips":tripPage(v);break;
            case "Service":servicePage(v);break;case "Insights":insightPage(v);break;case "Stations":stationPage(v);break;
            case "Receipts":receiptPage(v);break;case "Tools":toolsPage(v);break;case "Reports":reportsPage(v);break;
            case "Settings":settingsPage(v);break;case "Timeline":timelinePage(v);break;case "More":morePage(v);break;
            default:homePage(v);
        }
        bottomBar(root);
    }
    private void bottomBar(LinearLayout root){
        LinearLayout bar=row();bar.setPadding(dp(9),dp(5),dp(9),dp(5));bar.setBackgroundColor(WHITE);
        String[] icons={"⌂","▤","↗"};
        for(int i=0;i<nav.length;i++){
            final String target=nav[i];boolean selected=screen.equals(target);
            LinearLayout cell=column();cell.setGravity(Gravity.CENTER);cell.setPadding(dp(4),dp(3),dp(4),dp(3));
            TextView icon=text(icons[i],25,selected?BLUE:MUTED,true);icon.setGravity(Gravity.CENTER);cell.addView(icon);
            TextView label=text(target,12,selected?BLUE:MUTED,selected);label.setGravity(Gravity.CENTER);cell.addView(label);
            cell.setContentDescription(target+" tab"+(selected?", selected":""));cell.setFocusable(true);
            if(selected)cell.setBackground(bg(Color.rgb(232,239,255),16));
            cell.setOnClickListener(w->go(target));bar.addView(cell,new LinearLayout.LayoutParams(0,dp(62),1));
        }root.addView(bar);
    }
    private void heading(String title,String subtitle){content.addView(text(title,26,INK,true),below(12));content.addView(text(subtitle,13,MUTED,false),below(4));}
    private void welcome(){heading("Your garage starts here","Add your first vehicle, or import your data from the earlier RoadLedger screen.");
        LinearLayout c=card(content);c.addView(text("Track your vehicle. Know your costs.",20,INK,true));
        c.addView(text("Fill-ups, expenses, service, journeys and reports in one private space.",14,MUTED,false),below(8));
        action(c,"＋ Add your first vehicle",true,this::addVehicle);
        action(c,"Import / restore existing data",false,this::openClassic);
    }
    private void selectVehicle(){List<GarageDb.Vehicle> list=db.vehicles();String[] labels=new String[list.size()+1];for(int i=0;i<list.size();i++)labels[i]=list.get(i).name+" · "+list.get(i).make;labels[list.size()]="＋ Add another vehicle";
        new AlertDialog.Builder(this).setTitle("Switch vehicle").setItems(labels,(dialog,index)->{if(index==list.size())addVehicle();else{chosen=list.get(index).id;go("Home");}}).show();}
    private void addVehicle(){LinearLayout p=form();EditText name=field(p,"Vehicle nickname","My car",false);EditText make=field(p,"Make","Toyota",false);EditText model=field(p,"Model","Camry",false);
        confirm("Add vehicle",p,()->{if(name.getText().toString().trim().isEmpty())throw new IllegalArgumentException("Enter a nickname");chosen=db.addVehicle(name.getText().toString(),make.getText().toString(),model.getText().toString());go("Home");});}
    private String dist(GarageDb.Vehicle v){return v.unitSystem==1?"mi":"km";}
    private double factor(GarageDb.Vehicle v){return v.unitSystem==1?1.609344:1;}
    private String money(double amount){return String.format(Locale.US,"$%,.2f",amount);}
    private String num(double value){return String.format(Locale.US,"%,.1f",value);}
    private String day(long ms){return new SimpleDateFormat("MMM d, yyyy",Locale.US).format(new Date(ms));}
    private String stamp(long ms){return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US).format(new Date(ms));}
    private long parseTime(String value){try{SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.US);f.setLenient(false);return f.parse(value.trim()).getTime();}catch(Exception e){throw new IllegalArgumentException("Use date/time YYYY-MM-DD HH:MM");}}
    private double value(EditText field,String name){try{double d=Double.parseDouble(field.getText().toString().trim());if(!Double.isFinite(d)||d<0)throw new IllegalArgumentException("Enter a valid "+name);return d;}catch(NumberFormatException e){throw new IllegalArgumentException("Enter a valid "+name);}}
    private double optional(EditText field,String name){return field.getText().toString().trim().isEmpty()?0:value(field,name);}
    private LinearLayout form(){LinearLayout c=column();c.setPadding(dp(8),dp(8),dp(8),dp(8));return c;}
    private EditText field(LinearLayout parent,String label,String hint,boolean number){
        parent.addView(text(label,12,MUTED,true),below(12));EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(15);e.setTextColor(INK);e.setHint(hint);
        e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(bg(Color.rgb(243,246,251),12));if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        parent.addView(e,below(6));return e;
    }
    private Spinner spinner(LinearLayout parent,String label,String[] options,int selected){parent.addView(text(label,12,MUTED,true),below(12));Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options));s.setSelection(Math.min(Math.max(0,selected),options.length-1));parent.addView(s,below(5));return s;}
    private CheckBox check(LinearLayout parent,String label,boolean value){CheckBox b=new CheckBox(this);b.setText(label);b.setTextColor(INK);b.setChecked(value);parent.addView(b,below(8));return b;}
    private void confirm(String title,LinearLayout view,Runnable save){ScrollView scroll=new ScrollView(this);scroll.addView(view);AlertDialog a=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();a.setOnShowListener(x->a.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{try{save.run();a.dismiss();}catch(Exception e){toast(e.getMessage());}}));a.show();}
    private void navTile(LinearLayout parent,String title,String description,String target){LinearLayout tile=card(parent);tile.addView(text(title,16,INK,true));tile.addView(text(description,12,MUTED,false),below(5));tile.setOnClickListener(w->go(target));}
    private void tiles(LinearLayout c,String[] names,String[] pages){for(int i=0;i<names.length;i++)navTile(c,names[i],"Open "+names[i].toLowerCase(Locale.US),pages[i]);}
    private void record(LinearLayout parent,String title,String detail,Runnable edit){LinearLayout c=card(parent);c.addView(text(title,16,INK,true));c.addView(text(detail,12,MUTED,false),below(7));if(edit!=null)c.setOnClickListener(w->guard(edit));}
    private double totalFuel(long v){double sum=0;for(GarageDb.Fuel f:db.fuel(v))sum+=f.cost;return sum;}
    private double totalExpenses(long v){double sum=0;for(GarageDb.Expense e:db.expenses(v))sum+=e.amount;for(V7Store.ServiceLog s:store.serviceHistory(v))sum+=s.cost;return sum;}
    private double totalTrips(long v){double sum=0;for(GarageDb.Trip t:db.trips(v))sum+=t.endKm-t.startKm;return sum;}
    private V71HomeStats overviewStats(GarageDb.Vehicle v){
        V71HomeStats summary=new V71HomeStats(System.currentTimeMillis());
        for(GarageDb.Fuel f:db.fuel(v.id))summary.addFuel(f.date,f.cost);
        for(GarageDb.Expense e:db.expenses(v.id))summary.addExpense(e.date,e.amount);
        for(V7Store.ServiceLog l:store.serviceHistory(v.id))summary.addService(l.date,l.cost);
        for(GarageDb.Trip t:db.trips(v.id))summary.addTrip(t.date,Math.max(0,t.endKm-t.startKm));
        return summary;
    }
    private void monthlySpending(LinearLayout parent,V71HomeStats summary){
        LinearLayout panel=card(parent);
        panel.addView(text("SPENDING TREND",12,MUTED,true));
        panel.addView(text("Last 6 calendar months · fuel + other costs",12,MUTED,false),below(4));
        double max=summary.highestMonthSpend();
        if(max<=0){panel.addView(text("No spending recorded in this period yet.",14,MUTED,false),below(16));return;}
        V71HomeStats.Month[] months=summary.months();
        for(V71HomeStats.Month month:months){
            LinearLayout line=row();panel.addView(line,below(12));
            TextView monthText=text(month.label,12,MUTED,true);
            line.addView(monthText,new LinearLayout.LayoutParams(dp(40),dp(26)));
            ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
            bar.setIndeterminate(false);bar.setMax(1000);
            bar.setProgress((int)Math.round(month.totalSpend()/max*1000));
            bar.setProgressTintList(ColorStateList.valueOf(month==months[5]?GREEN:BLUE));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(232,239,248)));
            bar.setContentDescription(month.label+" spending "+money(month.totalSpend()));
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(12),1);
            bp.setMargins(dp(6),0,dp(9),0);line.addView(bar,bp);
            TextView dollars=text(money(month.totalSpend()),11,INK,true);
            dollars.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            line.addView(dollars,new LinearLayout.LayoutParams(dp(89),dp(26)));
        }
    }
    private void homePage(GarageDb.Vehicle v){
        heading("Your overview","One place for fuel, driving and maintenance.");
        V71HomeStats summary=overviewStats(v);
        LinearLayout hero=column();hero.setPadding(dp(21),dp(22),dp(21),dp(22));hero.setBackground(bg(INK,24));content.addView(hero,below(16));
        hero.addView(text(v.name+(v.year>0?"  ·  "+v.year:""),21,WHITE,true));
        hero.addView(text((v.make+" "+v.model).trim(),13,Color.rgb(202,217,240),false),below(4));
        hero.addView(text(num(db.currentOdometerKm(v.id)/factor(v))+" "+dist(v),33,WHITE,true),below(12));
        hero.addView(text("LATEST RECORDED ODOMETER",11,Color.rgb(169,199,234),true),below(3));
        action(content,"＋  Add Fill-up",true,()->fuelEditor(v,null));
        LinearLayout first=row();content.addView(first,below(15));
        metric(first,"THIS MONTH · ALL COSTS",money(summary.currentMonthSpend()),BLUE);
        GarageDb.Economy economy=db.economy(v.id);
        String unit=FuelUnits.consumptionUnit(FuelUnits.baseUnit(v.tank1Unit),v.consumptionUnit,v.unitSystem);
        double average=FuelUnits.displayConsumption(economy.litersPer100Km,FuelUnits.baseUnit(v.tank1Unit),v.consumptionUnit,v.unitSystem);
        String economyValue=economy.intervalValues.isEmpty()||!Double.isFinite(average)?"Pending":num(average)+" "+unit;
        metric(first,"AVERAGE ECONOMY",economyValue,GREEN);
        LinearLayout second=row();content.addView(second,below(10));
        metric(second,"MONTHLY TRIP DISTANCE",num(summary.currentMonthTripKm()/factor(v))+" "+dist(v),BLUE);
        metric(second,"FUEL SPEND · 6 MONTHS",money(totalLastSixMonthsFuel(summary)),GREEN);
        monthlySpending(content,summary);
        LinearLayout shortcuts=row();content.addView(shortcuts,below(12));
        Button fuel=button("Fuel history",false,()->go("Fuel"));
        Button trips=button("My trips",false,()->go("Trips"));
        LinearLayout.LayoutParams a=grow();a.rightMargin=dp(8);shortcuts.addView(fuel,a);shortcuts.addView(trips,grow());
        List<GarageDb.Fuel> recent=db.fuel(v.id);
        if(!recent.isEmpty()){
            GarageDb.Fuel last=recent.get(0);
            record(content,"Latest fill-up · "+money(last.cost),day(last.date)+" · "+num(last.odometer/factor(v))+" "+dist(v),()->fuelEditor(v,last));
        }
        LinearLayout alerts=card(content);int overdue=db.overdueServices(v.id,System.currentTimeMillis());
        alerts.addView(text(overdue>0?overdue+" maintenance items overdue":"Maintenance & reminders",16,overdue>0?Color.rgb(170,81,23):INK,true));
        alerts.addView(text("View upcoming service and completed work",12,MUTED,false),below(7));
        alerts.setContentDescription("Open maintenance and reminders");alerts.setOnClickListener(w->go("Service"));
        content.addView(text("YOUR VEHICLE TOOLS",12,MUTED,true),below(24));
        navTile(content,"Expenses","Costs, categories and service spending","Expenses");
        navTile(content,"Activity timeline","All vehicle entries in date order","Timeline");
        navTile(content,"More tools & settings","Maintenance, stations, receipts, backups and reports","More");
    }
    private double totalLastSixMonthsFuel(V71HomeStats summary){double total=0;for(V71HomeStats.Month m:summary.months())total+=m.fuel;return total;}
    private void metric(LinearLayout parent,String label,String stat,int color){
        LinearLayout x=column();x.setPadding(dp(13),dp(13),dp(11),dp(12));x.setBackground(bg(WHITE,18));
        TextView title=text(label,11,MUTED,true);title.setMaxLines(2);x.addView(title);
        TextView value=text(stat,18,color,true);value.setSingleLine(true);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setContentDescription(label+": "+stat);x.addView(value,below(8));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(97),1);p.rightMargin=dp(6);parent.addView(x,p);
    }
    private void fuelPage(GarageDb.Vehicle v){heading("Fuel & energy","Every fill-up, every tank, every unit.");LinearLayout top=card(content);
        GarageDb.Economy e=db.economy(v.id);String unit=FuelUnits.consumptionUnit(FuelUnits.baseUnit(v.tank1Unit),v.consumptionUnit,v.unitSystem);
        double consumption=FuelUnits.displayConsumption(e.litersPer100Km,FuelUnits.baseUnit(v.tank1Unit),v.consumptionUnit,v.unitSystem);
        top.addView(text("AVERAGE CONSUMPTION",12,MUTED,true));top.addView(text(e.intervalValues.isEmpty()?"Not enough full-tank entries":num(consumption)+" "+unit,e.intervalValues.isEmpty()?16:25,BLUE,true),below(8));
        action(top,"＋ Add fill-up",true,()->fuelEditor(v,null));
        List<GarageDb.Fuel> list=db.fuel(v.id);content.addView(text("RECENT FILL-UPS  ·  "+list.size(),12,MUTED,true),below(22));
        if(list.isEmpty())record(content,"No fill-ups yet","Record your first refuel or charging session",null);
        for(GarageDb.Fuel f:list){record(content,day(f.date)+" · "+money(f.cost),num(FuelUnits.fromBase(f.liters,f.sourceUnit))+" "+f.sourceUnit+"  ·  "+num(f.odometer/factor(v))+" "+dist(v)+"  ·  Tank "+f.tankNumber,()->fuelEditor(v,f));}
    }
    private void fuelEditor(GarageDb.Vehicle v,GarageDb.Fuel old){
        final String afterSave="Home".equals(screen)?"Home":"Fuel";
        LinearLayout p=form();
        EditText date=field(p,"DATE & TIME","YYYY-MM-DD HH:MM",false);date.setText(stamp(old==null?System.currentTimeMillis():old.date));
        EditText odo=field(p,"ODOMETER ("+dist(v)+")","Current odometer",true);if(old!=null)odo.setText(String.valueOf(old.odometer/factor(v)));
        EditText amount=field(p,"QUANTITY","Fuel or energy",true);if(old!=null)amount.setText(String.valueOf(FuelUnits.fromBase(old.liters,v.tankUnit(old.tankNumber))));
        EditText price=field(p,"PRICE BEFORE DISCOUNT","Total cost",true);if(old!=null)price.setText(String.valueOf(old.cost+old.discount));
        EditText discount=field(p,"DISCOUNT","0.00",true);if(old!=null)discount.setText(String.valueOf(old.discount));
        int tankDefault=old!=null&&old.tankNumber==2?1:0;
        String[] tankNames="Disabled".equals(v.tank2Fuel)?new String[]{"Tank 1 · "+v.tank1Fuel}:new String[]{"Tank 1 · "+v.tank1Fuel,"Tank 2 · "+v.tank2Fuel};
        Spinner tank=spinner(p,"FUEL TANK",tankNames,tankDefault);
        EditText subtype=field(p,"FUEL GRADE / ENERGY TYPE","Regular, Premium, E85...",false);if(old!=null)subtype.setText(old.fuelSubtype);
        EditText weather=field(p,"WEATHER (OPTIONAL)","Manual conditions",false);if(old!=null)weather.setText(old.weather);
        List<GarageDb.Station> stations=db.stations(v.id);String[] stationNames=new String[stations.size()+1];stationNames[0]="No station";int stationIndex=0;for(int i=0;i<stations.size();i++){stationNames[i+1]=stations.get(i).name;if(old!=null&&old.stationId==stations.get(i).id)stationIndex=i+1;}
        Spinner station=spinner(p,"STATION",stationNames,stationIndex);
        CheckBox full=check(p,"Full tank / full charge",old==null||old.full);CheckBox missed=check(p,"Previous fill-up is missing",old!=null&&old.missed);
        confirm(old==null?"New fill-up":"Edit fill-up",p,()->{
            int selectedTank=tank.getSelectedItemPosition()+1;String unit=v.tankUnit(selectedTank);
            double gross=value(price,"price"),off=optional(discount,"discount");if(off>gross)throw new IllegalArgumentException("Discount exceeds gross price");
            long sid=station.getSelectedItemPosition()==0?0:stations.get(station.getSelectedItemPosition()-1).id;
            db.saveFuelEntry(v.id,old==null?0:old.id,parseTime(date.getText().toString()),value(odo,"odometer")*factor(v),value(amount,"quantity")*FuelUnits.factor(unit),gross-off,
                full.isChecked(),missed.isChecked(),selectedTank,sid,unit,subtype.getText().toString(),off,weather.getText().toString());go(afterSave);
        });
    }
    private void expensePage(GarageDb.Vehicle v){heading("Expenses","See exactly where vehicle ownership costs go.");LinearLayout top=card(content);
        top.addView(text("TOTAL NON-FUEL SPEND",12,MUTED,true));top.addView(text(money(totalExpenses(v.id)),28,BLUE,true),below(8));action(top,"＋ Log expense",true,()->expenseEditor(v,null));
        action(content,"Filter: "+expenseFilter,false,()->{
            String[] categories=db.categoryOptions(v.id);String[] options=new String[categories.length+1];
            options[0]="All";System.arraycopy(categories,0,options,1,categories.length);
            new AlertDialog.Builder(this).setTitle("Expense categories").setItems(options,(d,index)->{expenseFilter=options[index];draw();}).show();
        });
        double filtered=0;int count=0;
        for(GarageDb.Expense e:db.expenses(v.id))if("All".equals(expenseFilter)||e.category.equals(expenseFilter)){
            filtered+=e.amount;count++;record(content,e.category+" · "+money(e.amount),day(e.date)+"  ·  "+e.note,()->expenseEditor(v,e));
        }
        record(content,"Shown: "+count+" expenses","Filtered expense total: "+money(filtered),null);
        action(content,"Manage custom categories",false,this::openClassic);
    }
    private void expenseEditor(GarageDb.Vehicle v,GarageDb.Expense old){LinearLayout p=form();EditText date=field(p,"DATE & TIME","YYYY-MM-DD HH:MM",false);date.setText(stamp(old==null?System.currentTimeMillis():old.date));
        String[] categories=db.categoryOptions(v.id);int idx=0;for(int i=0;i<categories.length;i++)if(old!=null&&categories[i].equals(old.category))idx=i;
        Spinner category=spinner(p,"CATEGORY",categories,idx);EditText cost=field(p,"TOTAL AMOUNT","0.00",true);EditText odo=field(p,"ODOMETER ("+dist(v)+", OPTIONAL)","0",true);
        EditText note=field(p,"DETAILS","Vendor / notes",false);if(old!=null){cost.setText(String.valueOf(old.amount));odo.setText(String.valueOf(old.odometer/factor(v)));note.setText(old.note);}
        confirm(old==null?"New expense":"Edit expense",p,()->{double o=optional(odo,"odometer")*factor(v);String c=categories[category.getSelectedItemPosition()];
            if(old==null)db.addExpense(v.id,parseTime(date.getText().toString()),c,value(cost,"cost"),o,note.getText().toString());
            else db.updateExpense(v.id,old.id,parseTime(date.getText().toString()),c,value(cost,"cost"),o,note.getText().toString());go("Expenses");});
    }
    private void servicePage(GarageDb.Vehicle v){heading("Maintenance","Schedules, completed work and recurring reminders.");
        LinearLayout actions=card(content);action(actions,"＋ Log completed service",true,()->serviceLogEditor(v));action(actions,"＋ Set upcoming reminder",false,()->reminderEditor(v));
        content.addView(text("UPCOMING SERVICE",12,MUTED,true),below(22));
        List<GarageDb.Service> reminders=db.services(v.id);int future=0;
        for(GarageDb.Service r:reminders){if(r.completed)continue;future++;int severity=r.priority(System.currentTimeMillis(),db.currentOdometerKm(v.id));
            String desc=(r.dueDate>0?"Date: "+day(r.dueDate):"")+(r.dueKm>0?"   Odometer: "+num(r.dueKm/factor(v))+" "+dist(v):"");
            record(content,(severity==0?"OVERDUE · ":severity==1?"SOON · ":"")+r.title,desc,()->{
                new AlertDialog.Builder(this).setTitle(r.title).setItems(new String[]{"Mark completed (schedule next recurrence)","Edit in classic management"},(d,i)->{
                    if(i==0){store.completeReminder(v.id,r,System.currentTimeMillis(),db.currentOdometerKm(v.id));draw();}else openClassic();}).show();
            });
        }
        if(future==0)record(content,"All caught up","No pending maintenance reminders",null);
        content.addView(text("SERVICE HISTORY",12,MUTED,true),below(24));
        for(V7Store.ServiceLog h:store.serviceHistory(v.id))record(content,h.title+" · "+money(h.cost),day(h.date)+" · "+h.vendor+" · "+num(h.odometerKm/factor(v))+" "+dist(v),()->serviceHistoryDetails(v,h));
    }
    private void serviceLogEditor(GarageDb.Vehicle v){LinearLayout p=form();EditText title=field(p,"SERVICE","Oil change, tires, inspection...",false);
        EditText when=field(p,"COMPLETED DATE & TIME","YYYY-MM-DD HH:MM",false);when.setText(stamp(System.currentTimeMillis()));
        EditText cost=field(p,"TOTAL COST","0.00",true);EditText odo=field(p,"ODOMETER ("+dist(v)+")","0",true);EditText vendor=field(p,"SHOP / VENDOR","Optional",false);
        EditText note=field(p,"NOTES","Parts, warranty, work performed",false);
        EditText days=field(p,"REPEAT EVERY N DAYS (OPTIONAL)","365",true);EditText km=field(p,"REPEAT EVERY N "+dist(v).toUpperCase(Locale.US)+" (OPTIONAL)","10000",true);
        confirm("Log service",p,()->{int repeat=(int)optional(days,"days");store.logAndSchedule(v.id,parseTime(when.getText().toString()),title.getText().toString(),vendor.getText().toString(),optional(odo,"odometer")*factor(v),optional(cost,"cost"),note.getText().toString(),repeat,optional(km,"interval")*factor(v));go("Service");});
    }
    private void serviceHistoryDetails(GarageDb.Vehicle v,V7Store.ServiceLog item){new AlertDialog.Builder(this).setTitle(item.title)
        .setMessage(day(item.date)+"\n"+item.vendor+"\nCost: "+money(item.cost)+"\n"+item.note)
        .setPositiveButton("Close",null).setNeutralButton("Delete",(dialog,i)->new AlertDialog.Builder(this).setTitle("Delete service record?").setPositiveButton("Delete",(x,j)->{store.deleteServiceHistory(v.id,item.id);draw();}).setNegativeButton("Cancel",null).show()).show();}
    private void reminderEditor(GarageDb.Vehicle v){LinearLayout p=form();EditText title=field(p,"REMINDER","Tire rotation",false);
        EditText due=field(p,"DUE DATE (YYYY-MM-DD)","2027-01-01",false);EditText odo=field(p,"DUE ODOMETER ("+dist(v)+")","0",true);
        EditText days=field(p,"RECUR EVERY N DAYS","0",true);EditText km=field(p,"RECUR EVERY N "+dist(v).toUpperCase(Locale.US),"0",true);
        EditText note=field(p,"NOTES","Optional",false);
        confirm("New service reminder",p,()->{
            long when=due.getText().toString().trim().isEmpty()?0:parseTime(due.getText().toString().trim()+" 12:00");
            long id=db.saveService(v.id,0,title.getText().toString(),when,optional(odo,"odometer")*factor(v),note.getText().toString(),false);
            store.setRecurrence(id,(int)optional(days,"recurrence"),optional(km,"recurrence")*factor(v));go("Service");
        });
    }
    private void tripPage(GarageDb.Vehicle v){heading("My trips","Manual odometer trips and opt-in GPS journeys.");
        LinearLayout top=card(content);top.addView(text("TOTAL TRACKED DISTANCE",12,MUTED,true));top.addView(text(num(totalTrips(v.id)/factor(v))+" "+dist(v),29,BLUE,true),below(8));
        action(top,"＋ Add manual trip",true,()->manualTrip(v));
        if(store.activeRoute(v.id)==0)action(top,"● Start GPS recording",false,()->startGps(v));
        else action(top,"■ Stop GPS recording",false,()->stopGps(v));
        content.addView(text("RECORDED GPS ROUTES",12,MUTED,true),below(22));
        List<V7Store.Route> routes=store.routes(v.id);
        if(routes.isEmpty())record(content,"No GPS journeys","Tap Start GPS recording to opt in",null);
        for(V7Store.Route route:routes){record(content,day(route.started)+" · "+route.purpose,
            num(route.distanceKm/factor(v))+" "+dist(v)+(route.completed?" · Finished":" · Recording"),
            ()->routeActions(v,route));}
        content.addView(text("MANUAL AND COMPLETED TRIPS",12,MUTED,true),below(22));
        for(GarageDb.Trip t:db.trips(v.id))record(content,day(t.date)+" · "+num((t.endKm-t.startKm)/factor(v))+" "+dist(v),t.note,()->tripActions(v,t));
        action(content,"Trip cost & range calculator",false,()->go("Tools"));
    }
    private void manualTrip(GarageDb.Vehicle v){LinearLayout p=form();EditText date=field(p,"DATE & TIME","YYYY-MM-DD HH:MM",false);date.setText(stamp(System.currentTimeMillis()));
        EditText start=field(p,"START ODOMETER ("+dist(v)+")","0",true);start.setText(num(db.currentOdometerKm(v.id)/factor(v)).replace(",",""));
        EditText end=field(p,"END ODOMETER ("+dist(v)+")","0",true);EditText note=field(p,"PURPOSE / NOTES","Personal or business",false);
        confirm("Add trip",p,()->{db.addTrip(v.id,parseTime(date.getText().toString()),value(start,"start")*factor(v),value(end,"end")*factor(v),note.getText().toString());go("Trips");});
    }
    private void tripActions(GarageDb.Vehicle v,GarageDb.Trip trip){new AlertDialog.Builder(this).setTitle("Trip · "+day(trip.date)).setItems(new String[]{"Delete trip","Back"},(d,i)->{if(i==0)new AlertDialog.Builder(this).setMessage("Delete this trip?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{db.deleteTrip(trip.id);go("Trips");}).show();}).show();}
    private void startGps(GarageDb.Vehicle v){
        if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},801);return;
        }
        new AlertDialog.Builder(this).setTitle("Record a GPS journey?")
        .setMessage("RoadLedger records route points while the foreground tracking notification is displayed. Routes remain on your device and in your backups. You control start and stop.")
        .setNegativeButton("Cancel",null).setPositiveButton("Start",(d,i)->{
            Intent intent=new Intent(this,V7TripService.class);intent.setAction(V7TripService.START);intent.putExtra(V7TripService.VEHICLE,v.id);intent.putExtra("purpose",store.settings(v.id).purpose);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);toast("GPS trip recording requested");go("Trips");
        }).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);
        if(request==802){toast(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED?"Reminder notifications enabled":"Notification permission was not granted");}
        if(request==801){GarageDb.Vehicle v=vehicle();if(v!=null&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)startGps(v);else toast("GPS requires precise location permission");}
    }
    private void stopGps(GarageDb.Vehicle v){Intent i=new Intent(this,V7TripService.class);i.setAction(V7TripService.STOP);i.putExtra(V7TripService.VEHICLE,v.id);startService(i);toast("GPS recording stop requested");go("Trips");}
    private void routeActions(GarageDb.Vehicle v,V7Store.Route route){new AlertDialog.Builder(this).setTitle("GPS journey")
        .setItems(new String[]{"Export route as GPX","Show route start on map"},(d,i)->{
            if(i==0){pendingRoute=route.id;Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/gpx+xml").putExtra(Intent.EXTRA_TITLE,"RoadLedger-route-"+route.id+".gpx");startActivityForResult(save,GPX);}
            else {List<V7Store.Point> pts=store.points(route.id);if(!pts.isEmpty()){V7Store.Point p=pts.get(0);openUrl(Uri.parse("geo:"+p.lat+","+p.lon+"?q="+p.lat+","+p.lon));}else toast("No valid GPS points recorded");}
        }).show();}
    private void openUrl(Uri uri){Intent i=new Intent(Intent.ACTION_VIEW,uri);try{startActivity(i);}catch(Exception e){toast("No compatible maps app available");}}
    private String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    private String gpx(long route){StringBuilder s=new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?><gpx version=\"1.1\" creator=\"RoadLedger\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>RoadLedger route</name><trkseg>");
        for(V7Store.Point p:store.points(route)){s.append("<trkpt lat=\"").append(p.lat).append("\" lon=\"").append(p.lon).append("\"><ele>").append(p.alt).append("</ele><time>").append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US){{setTimeZone(java.util.TimeZone.getTimeZone("UTC"));}}.format(new Date(p.time))).append("</time></trkpt>");}
        return s.append("</trkseg></trk></gpx>").toString();
    }
    private void insightPage(GarageDb.Vehicle v){heading("Insights","Costs and fuel performance, backed by your records.");
        LinearLayout totals=card(content);totals.addView(text("TOTAL OWNERSHIP SPEND",12,MUTED,true));totals.addView(text(money(totalFuel(v.id)+totalExpenses(v.id)),30,BLUE,true),below(8));
        totals.addView(text("Fuel: "+money(totalFuel(v.id))+"   Other: "+money(totalExpenses(v.id)),13,MUTED,false),below(8));
        GarageDb.Economy e=db.economy(v.id);double denom=e.totalDistanceKm;
        if(denom>0)totals.addView(text("Cost per distance: "+money((totalFuel(v.id)+totalExpenses(v.id))/(denom/factor(v)))+" / "+dist(v),13,MUTED,false),below(7));
        content.addView(text("MONTHLY SPENDING · LAST SIX MONTHS",12,MUTED,true),below(25));
        java.util.Calendar clock=java.util.Calendar.getInstance();clock.set(java.util.Calendar.DAY_OF_MONTH,1);clock.set(java.util.Calendar.HOUR_OF_DAY,0);clock.set(java.util.Calendar.MINUTE,0);clock.set(java.util.Calendar.SECOND,0);clock.set(java.util.Calendar.MILLISECOND,0);
        long[] starts=new long[7];for(int i=6;i>=0;i--){starts[i]=clock.getTimeInMillis();clock.add(java.util.Calendar.MONTH,-1);}
        double[] sums=new double[6];for(GarageDb.Fuel f:db.fuel(v.id))for(int j=0;j<6;j++)if(f.date>=starts[j]&&f.date<starts[j+1])sums[j]+=f.cost;
        for(GarageDb.Expense x:db.expenses(v.id))for(int j=0;j<6;j++)if(x.date>=starts[j]&&x.date<starts[j+1])sums[j]+=x.amount;
        for(V7Store.ServiceLog x:store.serviceHistory(v.id))for(int j=0;j<6;j++)if(x.date>=starts[j]&&x.date<starts[j+1])sums[j]+=x.cost;
        double max=0;for(double sum:sums)max=Math.max(max,sum);
        for(int i=0;i<6;i++){
            LinearLayout line=row();content.addView(line,below(10));String month=new SimpleDateFormat("MMM yy",Locale.US).format(new Date(starts[i]));
            TextView name=text(month,12,MUTED,true);line.addView(name,new LinearLayout.LayoutParams(dp(61),dp(27)));
            View bar=new View(this);bar.setBackground(bg(i==5?GREEN:BLUE,8));int width=max==0?0:Math.max(2,(int)((sums[i]/max)*150));line.addView(bar,new LinearLayout.LayoutParams(dp(width),dp(15)));
            TextView total=text("  "+money(sums[i]),12,INK,true);line.addView(total);
        }
        content.addView(text("FUEL ECONOMY",12,MUTED,true),below(26));
        for(int tank=1;tank<=2;tank++){
            if(tank==2&&"Disabled".equals(v.tank2Fuel))continue;
            GarageDb.Economy result=db.economy(v.id,tank);String unit=FuelUnits.consumptionUnit(FuelUnits.baseUnit(v.tankUnit(tank)),v.consumptionUnit,v.unitSystem);
            double rate=FuelUnits.displayConsumption(result.litersPer100Km,FuelUnits.baseUnit(v.tankUnit(tank)),v.consumptionUnit,v.unitSystem);
            record(content,"Tank "+tank+" · "+(result.intervalValues.isEmpty()?"Pending":num(rate)+" "+unit),result.intervalValues.size()+" completed full-to-full intervals",null);
        }
        action(content,"Full records & PDF report",false,()->go("Reports"));
    }
    private void stationPage(GarageDb.Vehicle v){heading("My stations","Saved fuel stops with manual prices, ratings and map shortcuts.");
        action(content,"＋ Add a fuel station",true,()->stationEditor(v,null));
        for(GarageDb.Station station:db.stations(v.id)){
            V7Store.StationMeta x=store.stationMeta(station.id);
            record(content,(x.favorite?"★  ":"")+station.name,station.address+(x.price>0?" · Last price "+money(x.price):"")+(x.rating>0?" · "+x.rating+"/5":""),()->{
                new AlertDialog.Builder(this).setTitle(station.name).setItems(new String[]{"Edit station, rating & price","Open map directions","Delete station"},(d,i)->{
                    if(i==0)stationEditor(v,station);
                    else if(i==1)openUrl(Uri.parse("geo:0,0?q="+Uri.encode(station.address.isEmpty()?station.name:station.address)));
                    else new AlertDialog.Builder(this).setMessage("Delete this saved station?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(z,j)->{db.deleteStation(v.id,station.id);go("Stations");}).show();
                }).show();
            });
        }
        record(content,"Prices are user-entered","Live provider prices and crowdsourced comparisons require a licensed data feed; none are presented as live.",null);
    }
    private void stationEditor(GarageDb.Vehicle v,GarageDb.Station old){LinearLayout p=form();EditText name=field(p,"STATION NAME","Shell",false);EditText address=field(p,"ADDRESS","Street, city",false);
        V7Store.StationMeta meta=old==null?new V7Store.StationMeta(false,0,0,0,0,0):store.stationMeta(old.id);
        EditText price=field(p,"LAST KNOWN UNIT PRICE","0.00",true);EditText rating=field(p,"RATING 0–5","0",true);CheckBox favorite=check(p,"Favorite station",meta.favorite);
        if(old!=null){name.setText(old.name);address.setText(old.address);price.setText(String.valueOf(meta.price));rating.setText(String.valueOf(meta.rating));}
        confirm(old==null?"New station":"Edit station",p,()->{
            long id=db.saveStation(v.id,old==null?0:old.id,name.getText().toString(),address.getText().toString());
            store.stationMeta(id,favorite.isChecked(),(int)optional(rating,"rating"),meta.lat,meta.lon,optional(price,"price"),System.currentTimeMillis());go("Stations");
        });
    }
    private void receiptPage(GarageDb.Vehicle v){heading("Receipts & documents","Photos are saved inside the RoadLedger database and native backups.");
        action(content,"＋ Add a receipt photo",true,()->{
            pendingVehicle=v.id;
            String[] choices={"Unlinked receipt","Link to latest fill-up","Link to latest expense","Link to latest service"};
            new AlertDialog.Builder(this).setTitle("Attach receipt to").setItems(choices,(d,index)->{
                pendingKind="Receipt";pendingRecordId=0;
                if(index==1&&!db.fuel(v.id).isEmpty()){pendingKind="Fuel";pendingRecordId=db.fuel(v.id).get(0).id;}
                if(index==2&&!db.expenses(v.id).isEmpty()){pendingKind="Expense";pendingRecordId=db.expenses(v.id).get(0).id;}
                if(index==3&&!store.serviceHistory(v.id).isEmpty()){pendingKind="Service";pendingRecordId=store.serviceHistory(v.id).get(0).id;}
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*");startActivityForResult(i,PHOTO);
            }).show();
        });
        List<V7Store.Attachment> files=store.attachments(v.id);
        for(V7Store.Attachment a:files)record(content,a.filename.isEmpty()?"Receipt #"+a.id:a.filename,day(a.capturedAt)+(a.recordId>0?" · "+a.kind+" #"+a.recordId:"")+(a.ocr.isEmpty()?" · Tap to extract text":" · OCR: "+a.ocr.substring(0,Math.min(90,a.ocr.length()))),()->receiptActions(v,a));
        if(files.isEmpty())record(content,"No receipts yet","Select a photo (up to 2 MB); it remains in your local backup",null);
    }
    private void receiptActions(GarageDb.Vehicle v,V7Store.Attachment a){new AlertDialog.Builder(this).setTitle(a.filename).setItems(new String[]{"View image","Extract text (offline ML Kit)","Delete attachment"},(d,i)->{
        if(i==0){byte[] bytes=store.attachmentBytes(v.id,a.id);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null){toast("Could not decode photo");return;}ImageView view=new ImageView(this);view.setImageBitmap(bitmap);view.setAdjustViewBounds(true);new AlertDialog.Builder(this).setView(view).setPositiveButton("Close",null).show();}
        else if(i==1)ocr(v.id,a.id);
        else new AlertDialog.Builder(this).setMessage("Delete this receipt and its OCR text?").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{store.deleteAttachment(v.id,a.id);draw();}).show();
    }).show();}
    private void ocr(long vehicle,long id){byte[] bytes=store.attachmentBytes(vehicle,id);Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(bitmap==null){toast("Cannot decode selected image");return;}
        com.google.mlkit.vision.text.TextRecognizer client=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        client.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(result->{store.setOcr(vehicle,id,result.getText());client.close();toast("Text extracted and saved");draw();})
            .addOnFailureListener(error->{client.close();toast("Could not recognize text: "+error.getMessage());});
    }
    private void toolsPage(GarageDb.Vehicle v){heading("Journey calculator","Estimate cost, distance and driving range from your real consumption.");
        LinearLayout p=card(content);EditText distance=field(p,"TRIP DISTANCE ("+dist(v)+")","120",true);
        EditText fuel=field(p,"CONSUMPTION (BASE QUANTITY / 100 KM)","7.5",true);
        GarageDb.Economy e=db.economy(v.id);if(!e.intervalValues.isEmpty())fuel.setText(String.valueOf(e.litersPer100Km));
        EditText price=field(p,"PRICE PER BASE QUANTITY UNIT","3.40",true);
        EditText available=field(p,"FUEL / ENERGY AVAILABLE (BASE UNIT)","40",true);
        action(p,"Calculate trip cost and range",true,()->{
            double distKm=value(distance,"distance")*factor(v),rate=value(fuel,"consumption"),unitPrice=value(price,"unit price"),remaining=value(available,"remaining quantity");
            String msg="Estimated journey cost: "+money(V7Logic.travelCost(distKm,rate,unitPrice))+"\n"+
                "Estimated driving range: "+num(V7Logic.rangeKm(remaining,rate)/factor(v))+" "+dist(v)+"\n\nBased on your input; not a live driving prediction.";
            new AlertDialog.Builder(this).setTitle("Journey estimate").setMessage(msg).setPositiveButton("OK",null).show();
        });
        action(content,"Open route planning in Maps",false,()->openUrl(Uri.parse("https://www.google.com/maps/dir/?api=1")));
    }
    private void reportsPage(GarageDb.Vehicle v){heading("Reports & export","Your data, portable and owned by you.");
        LinearLayout group=card(content);group.addView(text("Vehicle report",19,INK,true));
        group.addView(text("Fuel, expenses, maintenance and journeys with totals",13,MUTED,false),below(8));
        action(group,"↓ Save report as PDF",true,()->{pendingVehicle=v.id;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf").putExtra(Intent.EXTRA_TITLE,"RoadLedger-"+v.name+"-report.pdf");startActivityForResult(i,PDF);});
        action(group,"↓ Export data as CSV",false,()->{pendingVehicle=v.id;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE,"RoadLedger-"+v.name+"-report.csv");startActivityForResult(i,CSV);});
        navTile(content,"Full native backup / restore","ZIP snapshot, Fuelio import, and Google Drive or Dropbox via the Android document picker","Settings");
        record(content,"Privacy","No account, advertisements or telemetry are required. A selected cloud provider is accessed through Android's document picker; continuous sync is not enabled.",null);
    }
    private void settingsPage(GarageDb.Vehicle v){heading("Vehicle settings","Customize reminder windows, unit preferences and backups.");
        V7Store.Settings s=store.settings(v.id);LinearLayout p=card(content);
        EditText days=field(p,"WARN BEFORE SERVICE (DAYS)","14",true);days.setText(String.valueOf(s.warningDays));
        EditText km=field(p,"WARN BEFORE SERVICE ("+dist(v)+")","500",true);km.setText(String.valueOf(s.warningKm/factor(v)));
        CheckBox mute=check(p,"Mute this vehicle's maintenance notifications",s.muted);
        Spinner purpose=spinner(p,"DEFAULT TRIP PURPOSE",new String[]{"Personal","Business","Medical","Charity","Other"},java.util.Arrays.asList("Personal","Business","Medical","Charity","Other").indexOf(s.purpose));
        action(p,"Save notification preferences",true,()->{store.saveSettings(v.id,s.currency,(int)value(days,"warning days"),value(km,"warning distance")*factor(v),mute.isChecked(),s.bluetooth,purpose.getSelectedItem().toString());toast("Preferences saved");draw();});
        action(content,"Open full vehicle and tank management",false,this::openClassic);
        action(content,"Native ZIP backup / restore & Fuelio import",false,this::openClassic);
        action(content,"Enable Android reminder notifications",false,()->{
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},802);
            else toast("Notifications are already allowed by Android settings");
        });
        record(content,"Background reminders","WorkManager checks maintenance approximately daily. Alerts can be delayed by Android battery optimization.",null);
        record(content,"Bluetooth car pairing","Bluetooth preferences are reserved in the data model, but automatic trip starts are disabled until background location and reconnection behavior are validated.",null);
    }
    private void morePage(GarageDb.Vehicle v){heading("Explore RoadLedger","All your vehicle tools, one tap away.");
        String[] names={"Expenses","Insights","Timeline","Stations","Receipts","Tools","Reports","Settings"};
        for(String name:names)navTile(content,name,description(name),name);
        action(content,"Advanced Garage / Fuelio import",false,this::openClassic);
    }
    private String description(String name){switch(name){
        case "Expenses":return "Categorized ownership costs and history";
        case "Insights":return "Monthly spend and fuel economy analytics";
        case "Timeline":return "Fuel, expenses, services and trips by date";
        case "Stations":return "Saved locations, ratings and manual prices";
        case "Receipts":return "Attach images and scan text on-device";
        case "Tools":return "Distance, journey cost and range calculator";
        case "Reports":return "PDF/CSV exports, backup and restore";
        default:return "Vehicle preferences and maintenance notifications";
    }}
    private static final class Event {final long date;final String title,detail;Event(long d,String t,String x){date=d;title=t;detail=x;}}
    private void timelinePage(GarageDb.Vehicle v){heading("Activity timeline","All records together, from most recent to oldest.");
        List<Event> events=new ArrayList<>();for(GarageDb.Fuel x:db.fuel(v.id))events.add(new Event(x.date,"Fuel · "+money(x.cost),num(FuelUnits.fromBase(x.liters,x.sourceUnit))+" "+x.sourceUnit+" · Tank "+x.tankNumber));
        for(GarageDb.Expense x:db.expenses(v.id))events.add(new Event(x.date,x.category+" · "+money(x.amount),x.note));
        for(GarageDb.Trip x:db.trips(v.id))events.add(new Event(x.date,"Trip · "+num((x.endKm-x.startKm)/factor(v))+" "+dist(v),x.note));
        for(V7Store.ServiceLog x:store.serviceHistory(v.id))events.add(new Event(x.date,"Service · "+x.title,money(x.cost)+" · "+x.vendor));
        events.sort((a,b)->Long.compare(b.date,a.date));String last="";
        for(Event e:events){String month=new SimpleDateFormat("MMMM yyyy",Locale.US).format(new Date(e.date));if(!month.equals(last)){content.addView(text(month.toUpperCase(Locale.US),12,MUTED,true),below(20));last=month;}record(content,e.title,day(e.date)+" · "+e.detail,null);}
        if(events.isEmpty())record(content,"No activity yet","Add fuel, an expense, service or trip to begin",null);
    }
    private void openClassic(){startActivity(new Intent(this,MainActivity.class));}
    private GarageDb.Vehicle findVehicle(long id){for(GarageDb.Vehicle v:db.vehicles())if(v.id==id)return v;return null;}
    private String reportCsv(GarageDb.Vehicle v){StringBuilder b=new StringBuilder("type,date,title,amount,quantity,odometer_km,notes\n");
        for(GarageDb.Fuel f:db.fuel(v.id))b.append("fuel,").append(V7Logic.csv(day(f.date))).append(",").append(V7Logic.csv("Tank "+f.tankNumber)).append(",").append(f.cost).append(",").append(f.liters).append(",").append(f.odometer).append(",").append(V7Logic.csv(f.fuelSubtype)).append("\n");
        for(GarageDb.Expense e:db.expenses(v.id))b.append("expense,").append(V7Logic.csv(day(e.date))).append(",").append(V7Logic.csv(e.category)).append(",").append(e.amount).append(",,").append(e.odometer).append(",").append(V7Logic.csv(e.note)).append("\n");
        for(V7Store.ServiceLog h:store.serviceHistory(v.id))b.append("service,").append(V7Logic.csv(day(h.date))).append(",").append(V7Logic.csv(h.title)).append(",").append(h.cost).append(",,").append(h.odometerKm).append(",").append(V7Logic.csv(h.vendor+" / "+h.note)).append("\n");
        for(GarageDb.Trip t:db.trips(v.id))b.append("trip,").append(V7Logic.csv(day(t.date))).append(",").append(V7Logic.csv("Trip")).append(",,").append(t.endKm-t.startKm).append(",").append(t.endKm).append(",").append(V7Logic.csv(t.note)).append("\n");
        return b.toString();
    }
    private void writePdf(GarageDb.Vehicle v,OutputStream out)throws Exception{
        PdfDocument doc=new PdfDocument();try{
            ArrayList<String> lines=new ArrayList<>();lines.add("Vehicle: "+v.name+" · "+v.make+" "+v.model);
            lines.add("Generated: "+day(System.currentTimeMillis()));lines.add("Fuel spend: "+money(totalFuel(v.id))+"   Other spend: "+money(totalExpenses(v.id)));lines.add(" ");
            lines.add("FILL-UPS");for(GarageDb.Fuel f:db.fuel(v.id))lines.add(day(f.date)+"  ·  "+money(f.cost)+"  ·  "+num(f.liters)+" base units  ·  Tank "+f.tankNumber);
            lines.add(" ");lines.add("EXPENSES");for(GarageDb.Expense e:db.expenses(v.id))lines.add(day(e.date)+"  ·  "+e.category+"  ·  "+money(e.amount));
            lines.add(" ");lines.add("SERVICES");for(V7Store.ServiceLog e:store.serviceHistory(v.id))lines.add(day(e.date)+"  ·  "+e.title+"  ·  "+money(e.cost));
            lines.add(" ");lines.add("TRIPS");for(GarageDb.Trip t:db.trips(v.id))lines.add(day(t.date)+"  ·  "+num((t.endKm-t.startKm)/factor(v))+" "+dist(v)+"  ·  "+t.note);
            int index=0,page=1;Paint heading=new Paint(3);heading.setColor(INK);heading.setTypeface(Typeface.DEFAULT_BOLD);heading.setTextSize(20);
            Paint normal=new Paint(3);normal.setColor(Color.DKGRAY);normal.setTextSize(11);
            while(index<lines.size()){
                PdfDocument.PageInfo info=new PdfDocument.PageInfo.Builder(595,842,page++).create();PdfDocument.Page p=doc.startPage(info);
                p.getCanvas().drawText("ROADLEDGER · VEHICLE REPORT",32,49,heading);int y=82;
                while(index<lines.size()&&y<805){String s=lines.get(index++);if(s.length()>91)s=s.substring(0,88)+"...";p.getCanvas().drawText(s,32,y,normal);y+=19;}
                doc.finishPage(p);
            }
            doc.writeTo(out);
        }finally{doc.close();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();try{
            if(request==PHOTO){
                byte[] bytes;try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream buffer=new ByteArrayOutputStream()){
                    if(in==null)throw new IllegalArgumentException("Cannot read selected photo");byte[] chunk=new byte[16384];int size=0,n;
                    while((n=in.read(chunk))!=-1){size+=n;if(size>2_000_000)throw new IllegalArgumentException("Photo exceeds 2 MB maximum");buffer.write(chunk,0,n);}bytes=buffer.toByteArray();
                }
                String mime=getContentResolver().getType(uri);if(mime==null||!mime.startsWith("image/"))throw new IllegalArgumentException("Choose an image");
                store.attach(pendingVehicle,pendingKind,pendingRecordId,"receipt-"+System.currentTimeMillis(),mime,bytes,"");toast("Receipt safely added to local database and native backups");go("Receipts");
            }else if(request==GPX){try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IllegalArgumentException("Cannot open GPX document");out.write(gpx(pendingRoute).getBytes(StandardCharsets.UTF_8));}toast("GPX saved");}
            else if(request==PDF||request==CSV){GarageDb.Vehicle v=findVehicle(pendingVehicle);if(v==null)throw new IllegalArgumentException("Vehicle no longer exists");
                try(OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IllegalArgumentException("Cannot open report document");if(request==PDF)writePdf(v,out);else out.write(reportCsv(v).getBytes(StandardCharsets.UTF_8));}toast("Report saved");}
        }catch(Exception e){toast("Operation failed: "+e.getMessage());}
    }
}
