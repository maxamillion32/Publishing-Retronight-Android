package com.megotechnologies.publishing_retronight.news;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.megotechnologies.publishing_retronight.FragmentMeta;
import com.megotechnologies.publishing_retronight.MainActivity;
import com.megotechnologies.publishing_retronight.R;
import com.megotechnologies.publishing_retronight.interfaces.ZCFragmentLifecycle;
import com.megotechnologies.publishing_retronight.interfaces.ZCRunnable;
import com.megotechnologies.publishing_retronight.utilities.ImageProcessingFunctions;
import com.megotechnologies.publishing_retronight.utilities.MLog;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class FragmentNewsItemsList extends FragmentMeta implements ZCFragmentLifecycle, ZCRunnable {

    public int idStream;

    protected Thread threadLoadMoreChecker;

    protected Handler threadHandler = new Handler() {

        public void handleMessage(android.os.Message msg) {

            Bitmap bmp = (Bitmap) msg.obj;
            if (bmp != null) {
                ImageView iv = (ImageView) v.findViewById(msg.what);
                iv.setImageBitmap(bmp);
            } else {
                ImageView iv = (ImageView) v.findViewById(msg.what);
                iv.setImageDrawable(getResources().getDrawable(R.drawable.cover));
            }

        }

    };

    Thread thPictDownload, thGetLikes, thAddLike, thLoadMore;
    int IV_ID_PREFIX = 4000, TV_ID_PREFIX = 6000;
    LinearLayout llContainer;
    LinearLayout llAnalytics;
    TextView tvLikes, tvNumLikes, tvLoadMore;
    String streamName = null;
    int offset = 0;

    protected Handler displayHandler = new Handler() {

        public void handleMessage(android.os.Message msg) {

            switch (msg.what) {

                case 2:

                    try {
                        loadFromLocalDBProduct();
                    } catch (IllegalStateException e) {

                    }
                    if (!tvLoadMore.getText().toString().contains(MainActivity.LOAD_MORE_CAPTION_END)) {
                        tvLoadMore.setText(MainActivity.LOAD_MORE_CAPTION);
                    }

                    break;

                case 3:

                    tvLoadMore.setText(MainActivity.LOAD_MORE_CAPTION_END);

                default:
                    break;
            }


        }

    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // TODO Auto-generated method stub
        activity.lastCreatedActivity = MainActivity.SCREEN_STREAMS;

        v = inflater.inflate(R.layout.fragment_shop_itemslist, container, false);

        storeClassVariables();
        initUIHandles();
        initUIListeners();
        formatUI();

        try {
            loadFromLocalDBProduct();
        } catch (IllegalStateException e) {

        }

        return v;
    }

    @Override
    public void storeClassVariables() {
        // TODO Auto-generated method stub

    }

    @Override
    public void initUIHandles() {
        // TODO Auto-generated method stub
        llContainer = (LinearLayout) v.findViewById(R.id.ll_container);
        tvLoadMore = (TextView) v.findViewById(R.id.tv_loadmore);
    }

    @Override
    public void initUIListeners() {
        // TODO Auto-generated method stub

        tvLoadMore.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

                if (!tvLoadMore.getText().toString().contains(MainActivity.LOAD_MORE_CAPTION_END)) {

                    tvLoadMore.setText(MainActivity.LOAD_MORE_CAPTION_LOADING);

                    thLoadMore = new Thread(FragmentNewsItemsList.this);
                    thLoadMore.setName(MainActivity.TH_NAME_LOAD_MORE);
                    thLoadMore.start();

                    threadLoadMoreChecker = new Thread(FragmentNewsItemsList.this);
                    threadLoadMoreChecker.setName(MainActivity.TH_NAME_LOAD_MORE_CHECKER);
                    threadLoadMoreChecker.start();

                }

            }

        });

    }

    @Override
    public void formatUI() {
        // TODO Auto-generated method stub

        tvLoadMore.setTextSize(MainActivity.TEXT_SIZE_TILE);

    }

    void loadFromLocalDBProduct() {


        offset = 0;

        HashMap<String, String> map = new HashMap<String, String>();
        map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_STREAM);
        map.put(MainActivity.DB_COL_SRV_ID, String.valueOf(idStream));
        String _idStream = null;
        if (dbC.isOpen()) {
            dbC.isAvailale();
            _idStream = dbC.retrieveId(map);
            ArrayList<HashMap<String, String>> arrRecords = dbC.retrieveRecords(map);
            if (arrRecords.size() > 0) {
                map = arrRecords.get(0);

            }
        }

        final String strStream = streamName;

        MLog.log("Newsstream srv_id " + idStream);
        MLog.log("Newsstream db_id " + _idStream);
        MLog.log("Newsstream name " + streamName);

        map = new HashMap<String, String>();
        map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_ITEM);
        map.put(MainActivity.DB_COL_FOREIGN_KEY, _idStream);
        ArrayList<HashMap<String, String>> recordsItems = null;
        if (dbC.isOpen()) {
            dbC.isAvailale();
            recordsItems = dbC.retrieveRecords(map);
        }

        llContainer.removeAllViews();

        if (recordsItems.size() == 0) {

            AlertDialog.Builder alert = new AlertDialog.Builder(v.getContext());
            alert.setMessage(MainActivity.MSG_PRODUCT_NOT_AVAILABLE);
            alert.setTitle("Alert");
            alert.setPositiveButton(MainActivity.MSG_OK, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    //dismiss the dialog
                    activity.loadShop();
                }
            });
            alert.create().show();
            return;

        }

        TextView tvHead = null;

        if (streamName != null) {

            tvHead = new TextView(activity.context);
            tvHead.setText(streamName.toUpperCase());
            LinearLayout.LayoutParams paramsLLHead = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tvHead.setLayoutParams(paramsLLHead);
            tvHead.setTextColor(getResources().getColor(R.color.text_color));
            tvHead.setPadding(MainActivity.SPACING, MainActivity.SPACING, MainActivity.SPACING, MainActivity.SPACING);
            tvHead.setTextSize(TypedValue.COMPLEX_UNIT_SP, (float) (MainActivity.TEXT_SIZE_TITLE * 1.2));
            tvHead.setEllipsize(TextUtils.TruncateAt.END);
            tvHead.setSingleLine();
            tvHead.setGravity(Gravity.CENTER);
            llContainer.addView(tvHead);

        }

        for (int i = 0; i < recordsItems.size(); i++) {

            // Add Basic Row

            LinearLayout linL = new LinearLayout(activity.context);
            LinearLayout.LayoutParams paramsLin = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            if(i == 0) {
                paramsLin.setMargins(0, MainActivity.SPACING, 0, MainActivity.SPACING);
            } else {
                paramsLin.setMargins(0, 0, 0, MainActivity.SPACING);
            }

            linL.setLayoutParams(paramsLin);
            linL.setOrientation(GridLayout.HORIZONTAL);
            linL.setBackgroundColor(getResources().getColor(R.color.gray_verydark));
            llContainer.addView(linL);

            for (int j = i; j <= (i); j++) {


                if (j >= recordsItems.size()) {
                    return;
                } else {

                    offset++;

                    map = recordsItems.get(j);
                    String _idItem = map.get(MainActivity.DB_COL_ID);
                    String title = map.get(MainActivity.DB_COL_TITLE);
                    String desc = map.get(MainActivity.DB_COL_SUB);
                    final String idItem = map.get(MainActivity.DB_COL_SRV_ID);
                    Long ts = Long.parseLong(map.get(MainActivity.DB_COL_TIMESTAMP));
                    Date tsDate = new Date(ts);
                    String timestamp = new SimpleDateFormat("dd/MM/yyyy hh:mma").format(tsDate);

                    int rowHeight = ((MainActivity.SCREEN_WIDTH * 3) / 4);
                    int imgMargin = (MainActivity.SCREEN_WIDTH / 16);
                    int imgWidth = (activity.SCREEN_WIDTH/4);

                    final String idSrvStream = String.valueOf(idStream);

                    LinearLayout llLeft = new LinearLayout(activity.context);
                    LinearLayout.LayoutParams paramsLL = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
                    paramsLL.weight = 1;
                    llLeft.setLayoutParams(paramsLL);
                    llLeft.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            // TODO Auto-generated method stub

                            FragmentNewsItemDetails fragment = new FragmentNewsItemDetails();
                            fragment.idItem = Integer.parseInt(idItem);
                            fragment.idStream = Integer.parseInt(idSrvStream);
                            activity.fragMgr.beginTransaction()
                                    .add(((ViewGroup) getView().getParent()).getId(), fragment, MainActivity.SCREEN_SHOP_ITEM_DETAILS)
                                    .addToBackStack(MainActivity.SCREEN_SHOP_ITEM_DETAILS)
                                    .commit();

                        }

                    });
                    linL.addView(llLeft);

                    ImageView ivProduct = new ImageView(activity.context);
                    LinearLayout.LayoutParams paramsIvProduct = null;
                    paramsIvProduct = new LinearLayout.LayoutParams(imgWidth - 2, imgWidth - 2);
                    paramsIvProduct.setMargins(1, 1, 1, 1);
                    ivProduct.setLayoutParams(paramsIvProduct);
                    ivProduct.setId(IV_ID_PREFIX + j);
                    ivProduct.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    ivProduct.setPadding(2, 2, 2, 2);
                    ivProduct.setImageDrawable(getResources().getDrawable(R.drawable.cover));
                    try {
                        ivProduct.setCropToPadding(true);
                    } catch (NoSuchMethodError e) {

                    }
                    llLeft.addView(ivProduct);

                    LinearLayout llRight = new LinearLayout(activity.context);
                    paramsLL = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT);
                    paramsLL.weight = 3;
                    llRight.setLayoutParams(paramsLL);
                    llRight.setOrientation(LinearLayout.VERTICAL);
                    llRight.setOnClickListener(new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            // TODO Auto-generated method stub

                            FragmentNewsItemDetails fragment = new FragmentNewsItemDetails();
                            fragment.idItem = Integer.parseInt(idItem);
                            fragment.idStream = Integer.parseInt(idSrvStream);
                            activity.fragMgr.beginTransaction()
                                    .add(((ViewGroup) getView().getParent()).getId(), fragment, MainActivity.SCREEN_SHOP_ITEM_DETAILS)
                                    .addToBackStack(MainActivity.SCREEN_SHOP_ITEM_DETAILS)
                                    .commit();

                        }

                    });
                    linL.addView(llRight);

                    TextView tvTitle = new TextView(activity.context);
                    LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    tvTitle.setLayoutParams(titleParams);
                    tvTitle.setPadding(activity.SPACING/2, activity.SPACING/2, activity.SPACING/2, 0);
                    tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, MainActivity.TEXT_SIZE_TITLE);
                    tvTitle.setText(title);
                    tvTitle.setSingleLine();
                    tvTitle.setTextColor(getResources().getColor(R.color.text_color));
                    llRight.addView(tvTitle);

                    TextView tvDesc = new TextView(activity.context);
                    LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    tvDesc.setLayoutParams(descParams);
                    tvDesc.setPadding(activity.SPACING / 2, activity.SPACING / 2, activity.SPACING / 2, 0);
                    tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, MainActivity.TEXT_SIZE_TITLE - 2);
                    tvDesc.setText(desc);
                    tvDesc.setLines(2);
                    tvDesc.setEllipsize(TextUtils.TruncateAt.END);
                    tvDesc.setTextColor(getResources().getColor(R.color.text_color_sub));
                    llRight.addView(tvDesc);

                    TextView tvTime = new TextView(activity.context);
                    LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    tvTime.setLayoutParams(timeParams);
                    tvTime.setPadding(activity.SPACING/2, activity.SPACING/2, activity.SPACING/2, activity.SPACING/2);
                    tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, MainActivity.TEXT_SIZE_TITLE-4);
                    tvTime.setText(timestamp);
                    tvTime.setSingleLine();
                    tvTime.setTextColor(getResources().getColor(R.color.text_color_sub));
                    llRight.addView(tvTime);

                    map = new HashMap<String, String>();
                    map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_PICTURE);
                    map.put(MainActivity.DB_COL_FOREIGN_KEY, _idItem);
                    ArrayList<HashMap<String, String>> recordsPictures = null;
                    if (dbC.isOpen()) {
                        dbC.isAvailale();
                        recordsPictures = dbC.retrieveRecords(map);
                    }

                    String picture = "";

                    // If a picture exists, set it. Otherwise set app icon

                    if (recordsPictures.size() > 0) {

                        map = recordsPictures.get(0);
                        picture = map.get(MainActivity.DB_COL_PATH_TH);
                    }

                    MLog.log("Title = " + title + ", Picture = " + picture);

                    if (picture.length() > 0) {

                        if (activity.checkIfExistsInExternalStorage(picture)) {

                            String filePath = MainActivity.STORAGE_PATH + "/" + picture;
                            Bitmap bmp = (new ImageProcessingFunctions()).decodeSampledBitmapFromFile(filePath, MainActivity.IMG_TH_MAX_SIZE, MainActivity.IMG_TH_MAX_SIZE);
                            Message msg = new Message();
                            msg.obj = bmp;
                            msg.what = (IV_ID_PREFIX + j);
                            MLog.log("Sending message to " + msg.what + " " + msg.arg1);
                            threadHandler.sendMessage(msg);

                        } else {

                            thPictDownload = new Thread(this);
                            thPictDownload.setName(picture + ";" + (IV_ID_PREFIX + j));
                            thPictDownload.start();

                        }

                    } else {

                        Message msg = new Message();
                        msg.obj = null;
                        msg.what = (IV_ID_PREFIX + j);
                        msg.arg1 = Math.round(0);
                        MLog.log("Sending message to " + msg.what);
                        threadHandler.sendMessage(msg);

                    }

                }

            }

        }


    }


    @Override
    public void run() {
        // TODO Auto-generated method stub
        Looper.prepare();

        Thread t = Thread.currentThread();
        String tName = t.getName();

        if (t.getName().equals(MainActivity.TH_NAME_LOAD_MORE_CHECKER)) {


            Boolean RUN_FLAG = true;

            while (RUN_FLAG) {

                if (thLoadMore.getState().name().equals(MainActivity.TH_STATE_TERM)) {
                    RUN_FLAG = false;
                }

                try {
                    Thread.sleep(MainActivity.TH_CHECKER_DURATION);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }

            displayHandler.sendEmptyMessage(2);


        } else if (t.getName().equals(MainActivity.TH_NAME_LOAD_MORE)) {

            MLog.log("Starting Stream thread.. ");

            String jsonStr = "";
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = null;

            httppost = new HttpPost(MainActivity.API_INDI_STREAMS);
            jsonStr = "[{\"idProject\": \"" + MainActivity.PID + "\", \"idCountry\": \"" + activity.myCountryId + "\", \"idState\": \"" + activity.myStateId + "\", \"idCity\": \"" + activity.myCityId + "\", \"offset\": \"" + offset + "\", \"idStream\": \"" + idStream + "\"}]";

            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
            nameValuePairs.add(new BasicNameValuePair("params", jsonStr));
            MLog.log("Stream API=" + jsonStr);

            String responseString = null;

            try {
                httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
                HttpResponse response = httpclient.execute(httppost);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.getEntity().writeTo(out);
                out.close();
                responseString = out.toString();
                MLog.log(responseString);

            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (responseString != null) {

                jsonStr = responseString;

                try {

                    JSONObject jsonObj = new JSONObject(jsonStr);
                    if (jsonObj.getString("result").equals("success")) {


                        HashMap<String, String> map = new HashMap<String, String>();
                        map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_STREAM);
                        map.put(MainActivity.DB_COL_SRV_ID, idStream + "");

                        String _idStream = null;
                        if (dbC.isOpen()) {
                            dbC.isAvailale();
                            _idStream = dbC.retrieveId(map);
                        }

                        String valueStr = jsonObj.getString("value");
                        JSONArray jsonArr = new JSONArray(valueStr);

                        if (jsonArr.length() == 0) {
                            displayHandler.sendEmptyMessage(3);
                        }

                        for (int i = 0; i < jsonArr.length(); i++) {

                            JSONObject jsonObjItems = jsonArr.getJSONObject(i).getJSONObject("items");
                            JSONArray jsonArrPictures = jsonArr.getJSONObject(i).getJSONArray("pictures");
                            JSONArray jsonArrUrls = jsonArr.getJSONObject(i).getJSONArray("urls");
                            JSONArray jsonArrLocations = jsonArr.getJSONObject(i).getJSONArray("locations");
                            JSONArray jsonArrContacts = jsonArr.getJSONObject(i).getJSONArray("contacts");
                            JSONArray jsonArrAttachments = jsonArr.getJSONObject(i).getJSONArray("attachments");
                            String priceMapped = jsonArr.getJSONObject(i).getString("price");

                            String idSrvProductitems = jsonObjItems.getString("idProductitems");
                            String title = jsonObjItems.getString("title");
                            String subTitle = jsonObjItems.getString("subtitle");
                            String content = jsonObjItems.getString("content");
                            String timestamp = jsonObjItems.getString("timestampPublish");
                            String stock = jsonObjItems.getString("stockCurrent");
                            String size = jsonObjItems.getString("size");
                            String weight = jsonObjItems.getString("weight");
                            String sku = jsonObjItems.getString("sku");
                            String price = jsonObjItems.getString("price");

                            if (!priceMapped.equals("-1")) {
                                price = priceMapped;
                            }

                            map = new HashMap<String, String>();
                            map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_ITEM);
                            map.put(MainActivity.DB_COL_TITLE, title);
                            map.put(MainActivity.DB_COL_SRV_ID, idSrvProductitems);
                            map.put(MainActivity.DB_COL_SUB, subTitle);
                            map.put(MainActivity.DB_COL_CONTENT, content);
                            map.put(MainActivity.DB_COL_TIMESTAMP, timestamp);
                            map.put(MainActivity.DB_COL_STOCK, stock);
                            map.put(MainActivity.DB_COL_SIZE, size);
                            map.put(MainActivity.DB_COL_WEIGHT, weight);
                            map.put(MainActivity.DB_COL_SKU, sku);
                            map.put(MainActivity.DB_COL_PRICE, price);
                            map.put(MainActivity.DB_COL_FOREIGN_KEY, _idStream);


                            String _idItem = null;
                            if (dbC.isOpen()) {
                                dbC.isAvailale();
                                dbC.insertRecord(map);
                                _idItem = dbC.retrieveId(map);
                            }

                            if (jsonArrPictures.length() > 0) {

                                for (int k = 0; k < jsonArrPictures.length(); k++) {

                                    JSONObject jsonObjPicture = jsonArrPictures.getJSONObject(k);
                                    String pathOrig = jsonObjPicture.getString("pathOriginal");
                                    String pathProc = jsonObjPicture.getString("pathProcessed");
                                    String pathTh = jsonObjPicture.getString("pathThumbnail");

                                    String[] strArrOrig = pathOrig.split("/");
                                    String[] strArrProc = pathProc.split("/");
                                    String[] strArrTh = pathTh.split("/");

                                    map = new HashMap<String, String>();
                                    map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_PICTURE);
                                    map.put(MainActivity.DB_COL_PATH_ORIG, strArrOrig[strArrOrig.length - 1]);
                                    map.put(MainActivity.DB_COL_PATH_PROC, strArrProc[strArrProc.length - 1]);
                                    map.put(MainActivity.DB_COL_PATH_TH, strArrTh[strArrTh.length - 1]);
                                    map.put(MainActivity.DB_COL_FOREIGN_KEY, _idItem);

                                    if (dbC.isOpen()) {
                                        dbC.isAvailale();
                                        dbC.insertRecord(map);
                                    }

                                }

                            }

                            if (jsonArrUrls.length() > 0) {

                                for (int k = 0; k < jsonArrUrls.length(); k++) {

                                    JSONObject jsonObjUrl = jsonArrUrls.getJSONObject(k);
                                    String caption = jsonObjUrl.getString("caption");
                                    String value = jsonObjUrl.getString("value");

                                    map = new HashMap<String, String>();
                                    map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_URL);
                                    map.put(MainActivity.DB_COL_CAPTION, caption);
                                    map.put(MainActivity.DB_COL_URL, value);
                                    map.put(MainActivity.DB_COL_FOREIGN_KEY, _idItem);

                                    if (dbC.isOpen()) {
                                        dbC.isAvailale();
                                        dbC.insertRecord(map);
                                    }


                                }

                            }

                            if (jsonArrAttachments.length() > 0) {

                                for (int k = 0; k < jsonArrAttachments.length(); k++) {

                                    JSONObject jsonObjAttachment = jsonArrAttachments.getJSONObject(k);
                                    String caption = jsonObjAttachment.getString("caption");
                                    String value = jsonObjAttachment.getString("path");

                                    map = new HashMap<String, String>();
                                    map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_ATTACHMENT);
                                    map.put(MainActivity.DB_COL_CAPTION, caption);
                                    map.put(MainActivity.DB_COL_URL, value);
                                    map.put(MainActivity.DB_COL_FOREIGN_KEY, _idItem);

                                    if (dbC.isOpen()) {
                                        dbC.isAvailale();
                                        dbC.insertRecord(map);
                                    }


                                }

                            }

                            if (jsonArrLocations.length() > 0) {

                                for (int k = 0; k < jsonArrLocations.length(); k++) {

                                    JSONObject jsonObjLocation = jsonArrLocations.getJSONObject(k);
                                    String caption = jsonObjLocation.getString("caption");
                                    String location = jsonObjLocation.getString("location");

                                    map = new HashMap<String, String>();
                                    map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_LOCATION);
                                    map.put(MainActivity.DB_COL_CAPTION, caption);
                                    map.put(MainActivity.DB_COL_LOCATION, location);
                                    map.put(MainActivity.DB_COL_FOREIGN_KEY, _idItem);

                                    if (dbC.isOpen()) {
                                        dbC.isAvailale();
                                        dbC.insertRecord(map);
                                    }


                                }

                            }

                            if (jsonArrContacts.length() > 0) {

                                for (int k = 0; k < jsonArrContacts.length(); k++) {

                                    JSONObject jsonObjContact = jsonArrContacts.getJSONObject(k);
                                    String name = jsonObjContact.getString("name");
                                    String email = jsonObjContact.getString("email");
                                    String phone = jsonObjContact.getString("phone");

                                    map = new HashMap<String, String>();
                                    map.put(MainActivity.DB_COL_TYPE, MainActivity.DB_RECORD_TYPE_CONTACT);
                                    map.put(MainActivity.DB_COL_NAME, name);
                                    map.put(MainActivity.DB_COL_EMAIL, email);
                                    map.put(MainActivity.DB_COL_PHONE, phone);
                                    map.put(MainActivity.DB_COL_FOREIGN_KEY, _idItem);

                                    if (dbC.isOpen()) {
                                        dbC.isAvailale();
                                        dbC.insertRecord(map);
                                    }


                                }

                            }

                        }

                    }

                } catch (JSONException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

            }


        } else {


            String[] strArr = tName.split(";");

            String url = MainActivity.UPLOADS + "/" + strArr[0];
            int id = Integer.parseInt(strArr[1]);
            Bitmap bmp = (new ImageProcessingFunctions()).decodeSampledBitmapFromStream(url, MainActivity.IMG_TH_MAX_SIZE, MainActivity.IMG_TH_MAX_SIZE);
            Message msg = new Message();
            msg.obj = bmp;
            msg.what = id;
            MLog.log("Not from storage Sending message to " + id);
            threadHandler.sendMessage(msg);

            String filePath = MainActivity.STORAGE_PATH + "/" + strArr[0];
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
            }

            try {

                FileOutputStream out = new FileOutputStream(file.getAbsolutePath());
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
                out.close();

            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    @Override
    public void setRunFlag(Boolean value) {
        // TODO Auto-generated method stub

    }

}
