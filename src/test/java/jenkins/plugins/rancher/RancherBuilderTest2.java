package jenkins.plugins.rancher;

import jenkins.plugins.rancher.entity.StorageDrivers;
import jenkins.plugins.rancher.entity.Volume;
import jenkins.plugins.rancher.entity.Volumes;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * author: xiaobo
 * date:2018/3/15
 */
public class RancherBuilderTest2 {

    @Test
    public void test2() throws IOException {

        String environmentId = "1a54";
        String stackId = "1st78";
        RancherClient client = new RancherClient("http://110.185.101.106:9344/v2-beta",
                "5E14AC47302DE26899E6","J9d7nGF8B1YsxkrswZa91LerjTZkHu77VWRbRwo4");

      //  Optional<StorageDrivers> storageDrivers = client.storageDrivers(environmentId);


        //Optional<Volumes> volumes = client.volumes(environmentId, stackId);



//        Volume volume = new Volume();
//        volume.setName("data-test");
//        volume.setStorageDriverId("1sd1");
//        volume.setStackId(stackId);
//        Optional<Volume> testV = client.createVolume(environmentId,stackId,volume);

    //    client.createService()

        System.out.println("");

    }
}