& "C:\Users\Will\AppData\Roaming\Spotify\spotify_cli.exe" shuffle on
Start-Sleep -Seconds 2
& "C:\Users\Will\AppData\Roaming\Spotify\spotify_cli.exe" play spotify:playlist:37i9dQZF1DXcBWIGoYBM5M
Start-Sleep -Seconds 2
python -c "from backend.spotify_control import SpotifyControl; print(SpotifyControl.get_shuffle_repeat())"
