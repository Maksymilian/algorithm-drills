package collection.backfill;

import collection.backfill.model.Channel;
import collection.backfill.model.Status;

record ServiceState(Status status, String offerName, Channel channel) {
    ServiceState withStatus(Status next) {
        return new ServiceState(next, offerName, channel);
    }
}
