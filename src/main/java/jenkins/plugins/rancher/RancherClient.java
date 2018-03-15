package jenkins.plugins.rancher;

import jenkins.plugins.rancher.action.ServiceUpgrade;
import jenkins.plugins.rancher.entity.*;

import java.io.IOException;
import java.util.Optional;


/**
 * Rancher API client
 */
public class RancherClient extends HttpClient {

    public RancherClient(String endpoint) {
        super(endpoint, "", "");
    }

    /**
     * Get client instance
     *
     * @param endpoint:  rancher account key api address
     * @param accesskey: rancher account accesskey
     * @param secretKey: rancher account secretkey
     */
    public RancherClient(String endpoint, String accesskey, String secretKey) {
        super(endpoint, accesskey, secretKey);
    }

    public Optional<Environment> environment(String environmentId) throws IOException {
        return Optional.ofNullable(get("/projects/" + environmentId, Environment.class));
    }

    public Optional<Stacks> stacks(String environmentId) throws IOException {
        return Optional.ofNullable(get("/projects/" + environmentId + "/stacks", Stacks.class));
    }

    public Optional<Services> services(String stackId) throws IOException {
        return Optional.ofNullable(get("/stacks/" + stackId + "/services", Services.class));
    }

    public Optional<Service> service(String serviceId) throws IOException {
        return Optional.ofNullable(get("/services/" + serviceId, Service.class));
    }

    public Optional<Stack> createStack(Stack stack, String environmentId) throws IOException {
        return Optional.ofNullable(post(String.format("/projects/%s/stack", environmentId), stack, Stack.class));
    }

    public Optional<Service> createService(Service service, String environmentId, String stackId) throws IOException {
        service.setStackId(stackId);
        return Optional.ofNullable(post(String.format("/projects/%s/service", environmentId), service, Service.class));
    }

    public Optional<Service> upgradeService(String environmentId, String serviceId, ServiceUpgrade serviceUpgrade) throws IOException {
        return Optional.ofNullable(post(String.format("/projects/%s/services/%s/?action=upgrade", environmentId, serviceId), serviceUpgrade, Service.class));
    }

    public Optional<Service> finishUpgradeService(String environmentId, String serviceId) throws IOException {
        return Optional.ofNullable(post(String.format("/projects/%s/services/%s/?action=finishupgrade", environmentId, serviceId), Service.class));
    }

    public Optional<Volume> createVolume(String environmentId, String stackId,Volume volume) throws IOException {
        return Optional.ofNullable(post(String.format("/projects/%s/stacks/%s/volumes", environmentId, stackId),volume, Volume.class));
    }

    public Optional<StorageDrivers> storageDrivers(String environmentId) throws IOException {
        return Optional.ofNullable(get(String.format("/projects/%s/storagedrivers", environmentId), StorageDrivers.class));
    }


    public Optional<Volumes> volumes(String environmentId, String stackId) throws IOException {
        return Optional.ofNullable(get(String.format("/projects/%s/stacks/%s/volumes",environmentId,stackId), Volumes.class));
    }

    public Optional<VolumeTemplates> volumeTemplates(String environmentId, String stackId) throws IOException {
        return Optional.ofNullable(get(String.format("/projects/%s/stacks/%s/volumetemplates", environmentId, stackId), VolumeTemplates.class));
    }

    public Optional<VolumeTemplate> createVolumeTemplate(String environmentId, String stackId,VolumeTemplate volumeTemplate) throws IOException {
        return Optional.ofNullable(post(String.format("/projects/%s/stacks/%s/volumetemplates", environmentId, stackId),volumeTemplate,VolumeTemplate.class));
    }


}
