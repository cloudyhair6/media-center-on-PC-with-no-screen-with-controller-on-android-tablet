import asyncio
import winrt.windows.media.control as wmc

async def main():
    manager = await wmc.GlobalSystemMediaTransportControlsSessionManager.request_async()
    session = manager.get_current_session()
    if session:
        print('App:', session.source_app_user_model_id)

asyncio.run(main())
