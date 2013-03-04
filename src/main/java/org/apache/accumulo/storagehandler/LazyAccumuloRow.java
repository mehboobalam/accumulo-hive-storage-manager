package org.apache.accumulo.storagehandler;

import org.apache.hadoop.hive.serde2.lazy.ByteArrayRef;
import org.apache.hadoop.hive.serde2.lazy.LazyFactory;
import org.apache.hadoop.hive.serde2.lazy.LazyObject;
import org.apache.hadoop.hive.serde2.lazy.LazyStruct;
import org.apache.hadoop.hive.serde2.lazy.objectinspector.LazySimpleStructObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.StructField;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * User: bfemiano
 * Date: 3/2/13
 * Time: 2:26 AM
 */
public class LazyAccumuloRow extends LazyStruct{

    private AccumuloHiveRow row;
    private List<String> fetchCols;
    private ArrayList<Object> cachedList = new ArrayList<Object>();

    private static final Pattern COLON = Pattern.compile("[:]");
    private static final Logger log = Logger.getLogger(LazyAccumuloRow.class);

    public LazyAccumuloRow(LazySimpleStructObjectInspector inspector) {
        super(inspector);
    }

    public void init(AccumuloHiveRow hiveRow, List<String> fetchCols) {
        this.row = hiveRow;
        this.fetchCols = fetchCols;
        setParsed(false);

    }

    private void parse() {
        if(getFields() == null) {
            List<? extends StructField> fieldRefs =
                    getInspector().getAllStructFieldRefs();
            setFields(new LazyObject[fieldRefs.size()]);
            for (int i = 0; i < getFields().length; i++) {
                String colQualPair = fetchCols.get(i);
                //only supports fam:qual pairs for now. cell mapped column familes can come later.
                getFields()[i] = LazyFactory.createLazyObject(fieldRefs.get(i).getFieldObjectInspector());
            }
            setFieldInited(new boolean[getFields().length]);
        }
        Arrays.fill(getFieldInited(), false);
        setParsed(true);
    }

    @Override
    public Object getField(int id) {
        if(!getParsed()) {
            parse();
        }
        return uncheckedGetField(id);
    }

    private Object uncheckedGetField(int id) {
        if(!getFieldInited()[id]) {
            getFieldInited()[id] = true;
            ByteArrayRef ref;
            String famQualPair = fetchCols.get(id);

            if(AccumuloSerde.isKeyField(famQualPair)) {
                ref = new ByteArrayRef();
                ref.setData(row.getRowId().getBytes());
            } else {
                String[] famQualPieces = COLON.split(famQualPair);
                if (famQualPieces.length != 2)
                    throw new IllegalArgumentException("Malformed famQualPair: " + famQualPair);
                byte[] val = row.getValue(famQualPieces[0], famQualPieces[1]);
                if (val == null){
                    return null;
                } else {
                    ref = new ByteArrayRef();
                    ref.setData(val);
                }
            }
            getFields()[id].init(ref, 0, ref.getData().length);
        }
        return getFields()[id].getObject();
    }

    @Override
    public ArrayList<Object> getFieldsAsList() {
        if(!getParsed()) {
            parse();
        }
        cachedList.clear();
        for (int i = 0; i < getFields().length; i++) {
            cachedList.add(uncheckedGetField(i));
        }
        return cachedList;
    }
}
