package de.mirkosertic.gameengine.gwt;

import com.google.gwt.media.client.Audio;

import de.mirkosertic.gameengine.core.GameResource;
import de.mirkosertic.gameengine.core.GameResourceCache;
import de.mirkosertic.gameengine.core.GameResourceType;
import de.mirkosertic.gameengine.types.ResourceName;
import de.mirkosertic.gameengine.sound.GameSoundSystem;

public class GWTGameSoundSystem implements GameSoundSystem<Audio> {

    private GameResourceCache resourceCache;

    GWTGameSoundSystem(GameResourceCache aResourceCache) {
        resourceCache = aResourceCache;
    }

    @Override
    public Audio play(ResourceName aResourceName) {
        try {
            GameResource theResource = resourceCache.getResourceFor(aResourceName);
            if (theResource != null && theResource.getType() == GameResourceType.SOUND) {
                Audio theAudio = ((GWTAudioResource) theResource).getAudio();
                theAudio.play();
                return theAudio;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public void stop(Audio aSoundObject) {
    }
}
