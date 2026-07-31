import asyncio
from winrt.windows.media.control import GlobalSystemMediaTransportControlsSessionManager

async def main():
    try:
        manager = await GlobalSystemMediaTransportControlsSessionManager.request_async()
        sessions = manager.get_sessions()
        print(f"Total sessions: {len(sessions)}")
        for i, session in enumerate(sessions):
            print(f"Session {i}: {session.source_app_user_model_id}")
            if "Spotify" in session.source_app_user_model_id:
                info = session.get_playback_info()
                print(f"  Shuffle Active: {info.is_shuffle_active}")
                print(f"  Repeat Mode: {int(info.auto_repeat_mode)}")
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    asyncio.run(main())
