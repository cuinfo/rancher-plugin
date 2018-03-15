package jenkins.plugins.rancher;

import jenkins.plugins.rancher.entity.*;
import jenkins.plugins.rancher.util.Parser;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
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


        VolumeTemplate volumeTemplate  = new VolumeTemplate();
        volumeTemplate.setDriver("rancher-nfs");
        volumeTemplate.setStackId(stackId);
        volumeTemplate.setName("data-test");

        Optional<VolumeTemplate> createVolumeTemplate  = client.createVolumeTemplate(environmentId,stackId,volumeTemplate);

        Volume volume = new Volume();
        volume.setName("data-test-test");
        volume.setStorageDriverId("1sd1");
        volume.setStackId(stackId);
        volume.setVolumeTemplateId(createVolumeTemplate.get().getId());
        Optional<Volume> createVolume = client.createVolume(environmentId,stackId,volume);


        Service service = new Service();
        service.setName("sys-test");
        String dockerUUID = String.format("docker:%s", "busybox");

        LaunchConfig launchConfig = new LaunchConfig();

        launchConfig.setImageUuid(dockerUUID);
        launchConfig.setDataVolumes(new ArrayList<String>(){{
            add("data-test:/var/test");
        }});

        service.setLaunchConfig(launchConfig);
        Optional<Service> serviceInstance = client.createService(service, environmentId, stackId);

        System.out.println("");

    }
}