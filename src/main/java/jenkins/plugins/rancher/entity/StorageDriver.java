package jenkins.plugins.rancher.entity;

/**
 * author: xiaobo
 * date:2018/3/13
 */
public class StorageDriver extends Resource{


    public StorageDriver(){
        this("storageDriver");
    }

    public StorageDriver(String type) {
        super(type);
    }
}
