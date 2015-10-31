package ru.ipmavlutov.metallsensor.Graphs;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.util.ArrayList;
import java.util.Arrays;

import ru.ipmavlutov.metallsensor.Activity.DeviceControlActivity;
import ru.ipmavlutov.metallsensor.R;


public class Graph extends AppCompatActivity {
    Button get_first_date, get_second_date;

     String[] dateArr;
     double[] signalArr;
     double[] tempArr;
    Cursor c;
   int check;
    LineChart mChart;
    DeviceControlActivity.DBHelper BDH;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);
        get_first_date = (Button) findViewById(R.id.get_first_date);
        get_second_date = (Button) findViewById(R.id.get_second_date);
        mChart= (LineChart) findViewById(R.id.chart);


        BDH = new DeviceControlActivity.DBHelper(this);

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.get_first_date:
                        check=1;
                        Runnable r = new MyThread();
                        new Thread(r).run();
                        setData(dateArr, signalArr, tempArr, c.getCount());
                        mChart.invalidate();
                        break;
                    case R.id.get_second_date:
                        check=2;
                        Runnable t = new MyThread();
                        new Thread(t).run();
                        setData(dateArr, signalArr, tempArr, c.getCount());
                        mChart.invalidate();
                        break;
                }

            }

        };
        get_first_date.setOnClickListener(clickListener);
        get_second_date.setOnClickListener(clickListener);
    }

    public class MyThread implements Runnable {

        public void run() {
            // здесь пишем код, который будет исполняться в отдельном потоке
            // далее я вызываю два статических метода одного из своих классов (самописный класс)))

            SQLiteDatabase db = BDH.getWritableDatabase();
            c = db.query("mytable", null, null, null, null, null, null);
            if (c.moveToFirst()) {

                // определяем номера столбцов по имени в выборке
                dateArr = new String[c.getCount() + 1];
                signalArr = new double[c.getCount() + 1];
                tempArr = new double[c.getCount() + 1];

                int dateColIndex = c.getColumnIndex("date");
                int signalColIndex = c.getColumnIndex("signal");
                int tempColIndex = c.getColumnIndex("temperature");
                for (int i = 0; i < c.getCount(); i++) {

                    dateArr[i] = c.getString(dateColIndex);

                    signalArr[i] = c.getDouble(signalColIndex);
                    tempArr[i] = c.getDouble(tempColIndex);
                    Log.d("TAG", "i:=" + i + "D:" + dateArr[i] + " S:" + signalArr[i] + " T:" + tempArr[i]);
                    c.moveToNext();
                }
                //setData(dateArr, signalArr, tempArr,c.getCount());

            } else
                c.close();
        }


    }

    private void setData(String[] dateArr, double[] signalArr, double[] tempArr,int size) {

        ArrayList<String> xVals = new ArrayList<>();
        xVals.addAll(Arrays.asList(dateArr).subList(0, size));

        ArrayList<Entry> yVals1 = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            yVals1.add(new Entry((float) signalArr[i], i));
        }

        LineDataSet set1 = new LineDataSet(yVals1, "Signal");
        set1.setAxisDependency(YAxis.AxisDependency.LEFT);
        set1.setColor(ColorTemplate.getHoloBlue());
        set1.setCircleColor(Color.WHITE);
        set1.setLineWidth(2f);
        set1.setCircleSize(3f);
        set1.setFillAlpha(65);
        set1.setFillColor(ColorTemplate.getHoloBlue());
        set1.setHighLightColor(Color.rgb(244, 117, 117));
        set1.setDrawCircleHole(false);


        ArrayList<Entry> yVals2 = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            yVals2.add(new Entry((float) tempArr[i], i));
        }
        LineDataSet set2 = new LineDataSet(yVals2, "Temperature");
        set2.setAxisDependency(YAxis.AxisDependency.RIGHT);
        set2.setColor(Color.RED);
        set2.setCircleColor(Color.WHITE);
        set2.setLineWidth(2f);
        set2.setCircleSize(3f);
        set2.setFillAlpha(65);
        set2.setFillColor(Color.RED);
        set2.setDrawCircleHole(false);
        set2.setHighLightColor(Color.rgb(244, 117, 117));


        ArrayList<LineDataSet> dataSets = new ArrayList<>();
        if (check==1){
        dataSets.add(set1);}
        if(check==2){
            dataSets.add(set2);
        }


        // create a data object with the datasets
        LineData data = new LineData(xVals, dataSets);

        data.setValueTextColor(Color.BLACK);
        data.setValueTextSize(9f);

        // set data
        mChart.setData(data);

    }
}



