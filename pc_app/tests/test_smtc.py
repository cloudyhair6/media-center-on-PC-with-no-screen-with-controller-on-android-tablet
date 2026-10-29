import asyncio
import winrt.windows.media.control as wmc

async def main():
    manager = await wmc.GlobalSystemMediaTransportControlsSessionManager.request_async()
    session = manager.get_current_session()
    if session:
        props = await session.try_get_media_properties_async()
        print('Title:', props.title)
        print('Artist:', props.artist)
        print('AlbumTitle:', props.album_title)
        print('Subtitle:', props.subtitle)
        print('TrackNumber:', props.track_number)

asyncio.run(main())
