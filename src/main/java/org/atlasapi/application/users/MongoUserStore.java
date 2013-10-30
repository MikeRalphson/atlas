package org.atlasapi.application.users;

import static com.metabroadcast.common.persistence.mongo.MongoConstants.ID;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.SINGLE;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.UPSERT;
import static com.metabroadcast.common.persistence.mongo.MongoBuilders.where;

import org.atlasapi.media.common.Id;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.social.model.UserRef;
import com.metabroadcast.common.social.model.translator.UserRefTranslator;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class MongoUserStore implements UserStore {

    private DBCollection users;
    private UserTranslator translator;
    private UserRefTranslator userRefTranslator;
    
    private final Function<DBObject, User> translatorFunction = new Function<DBObject, User>() {

        @Override
        public User apply(DBObject dbo) {
            return translator.fromDBObject(dbo);
        }
    };

    public MongoUserStore(DatabasedMongo mongo) {
        this.users = mongo.collection("users");
        this.userRefTranslator = new UserRefTranslator();
        this.translator = new UserTranslator(userRefTranslator);
    }
    
    @Override
    public Optional<User> userForRef(UserRef ref) {
        return Optional.fromNullable(translator.fromDBObject(users.findOne(userRefTranslator.toQuery(ref, "userRef").build())));
    }

    @Override
    public Optional<User> userForId(Id id) {
        return Optional.fromNullable(translator.fromDBObject(users.findOne(where().idEquals(id.longValue()).build())));
    }

    @Override
    public void store(User user) {
        store(translator.toDBObject(user));
    }

    public void store(final DBObject dbo) {
        this.users.update(new BasicDBObject(ID, dbo.get(ID)), dbo, UPSERT, SINGLE);
    }

    @Override
    public Iterable<User> usersFor(Iterable<Id> ids) {
        Iterable<Long> idLongs = Iterables.transform(ids, Id.toLongValue());
        return Iterables.transform(users.find(where().longIdIn(idLongs).build()), 
                translatorFunction);
    }

    @Override
    public Iterable<User> allUsers() {
        return Iterables.transform(users.find(where().build()), translatorFunction);
    }

}
