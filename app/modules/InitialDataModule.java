package modules;

import com.google.inject.AbstractModule;
import modules.initial.Initiable;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class InitialDataModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger("InitialDataModule");

    @Override
    protected void configure() {
        Reflections reflections = new Reflections("modules.initial");

        reflections.getSubTypesOf(Initiable.class)
                .stream()
                .filter(aClass -> !aClass.isAnnotationPresent(Deprecated.class))
                .forEach(initiable -> {
                    logger.info("Found initial data class {}. Trying to load or ignore.", initiable.getCanonicalName());

                    bind(initiable).asEagerSingleton();
                });
    }

}
