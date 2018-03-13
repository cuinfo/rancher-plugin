package jenkins.plugins.rancher.entity;

/**
 * @author xiaob@inphase.com
 * @date 2018/3/13
 */
public class Volume extends Resource {

    String stackId;
    String storageDriverId;
    String driver="";



    public Volume() {
        super("volume");
    }

    public String getStackId() {
        return stackId;
    }

    public void setStackId(String stackId) {
        this.stackId = stackId;
    }

    public String getStorageDriverId() {
        return storageDriverId;
    }

    public void setStorageDriverId(String storageDriverId) {
        this.storageDriverId = storageDriverId;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }
}
