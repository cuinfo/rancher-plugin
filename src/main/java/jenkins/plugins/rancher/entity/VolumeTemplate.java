package jenkins.plugins.rancher.entity;

/**
 * @author xiaob@inphase.com
 * @date 2018/3/15
 */
public class VolumeTemplate extends Resource {
    public VolumeTemplate() {
        super("volumeTemplate");
    }
    String stackId;
    String driver;
    boolean external;
    boolean perContainer;
    public String getStackId() {
        return stackId;
    }

    public void setStackId(String stackId) {
        this.stackId = stackId;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public boolean getExternal() {
        return external;
    }

    public void setExternal(boolean external) {
        this.external = external;
    }

    public boolean getPerContainer() {
        return perContainer;
    }

    public void setPerContainer(boolean perContainer) {
        this.perContainer = perContainer;
    }
}
