/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package bftsmart.reconfiguration.views;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 *
 * @author eduardo
 */
public class DefaultViewStorage implements ViewStorage {

    private String path = "";

    public DefaultViewStorage() {
        String sep = System.getProperty("file.separator");
        path = System.getProperty("user.dir") + sep + "config";
        File f = new File(path);
        if (!f.exists()) {
            f.mkdirs();
        }
        path = path + sep + "currentView";
    }

    @Override
    public boolean storeView(View view) {
        if (!view.equals(readView())) {
            File f = new File(path);
            try {
                ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
                oos.writeObject(view);
                oos.flush();
                oos.close();
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public View readView() {
        File f = new File(path);
        if (!f.exists()) {
            return null;
        }
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
            View ret = (View) ois.readObject();
            ois.close();
            return ret;
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] getBytes(View view) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4);
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(view);
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    public View getView(byte[] bytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bais);
            return (View) ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }
}
