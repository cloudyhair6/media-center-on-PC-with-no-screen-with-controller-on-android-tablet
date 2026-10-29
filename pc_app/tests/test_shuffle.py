import asyncio
from winrt.windows.media.control import GlobalSystemMediaTransportControlsSessionManager

async def main():
    try:
        manager = await GlobalSystemMediaTransportControlsSessionManager.request_async()
        session = manager.get_current_session()
        if session:
            print(f"Current Session App: {session.source_app_user_model_id}")
            if "Spotify" in session.source_app_user_model_id:
                info = session.get_playback_info()
                print(f"Shuffle Active: {info.is_shuffle_active}")
                print(f"Repeat Mode: {int(info.auto_repeat_mode)}")
                if info.auto_repeat_mode.value == 0:
                    print("Repeat is OFF")
                elif info.auto_repeat_mode.value == 1:
                    print("Repeat is TRACK")
                elif info.auto_repeat_mode.value == 2:
                    print("Repeat is CONTEXT")
                else:
                    print(f"Repeat is unknown: {info.auto_repeat_mode}")
            else:
                print("Not Spotify.")
        else:
            print("No current session.")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    asyncio.run(main())
