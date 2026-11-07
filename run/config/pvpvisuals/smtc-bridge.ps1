			$ErrorActionPreference = 'Stop'
$lastErr = ''
function Emit($json) { Write-Output ([Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($json))) }
function Report($m) { if ($m -ne $script:lastErr) { $script:lastErr = $m; Emit (ConvertTo-Json @{ error = [string]$m } -Compress) } }
			$parentPid = 22248
						Add-Type -AssemblyName System.Runtime.WindowsRuntime
			$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
			$null = [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime]
			$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
			function Await($op, $type) {
			  $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
			  [void]$task.Wait(5000)
			  if ($task.IsCompleted) { return $task.Result } else { return $null }
			}
			try { $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]) } catch { Report ('init: ' + $_.Exception.Message); exit 1 }
			if ($mgr -eq $null) { Report 'SessionManager.RequestAsync returned null (Windows 10 1809+ required)'; exit 1 }
			$lastKey = ''; $cover = ''; $coverTries = 0; $n = 0
			while ($true) {
			  $n++
			  if ($n % 10 -eq 0) { if (-not (Get-Process -Id $parentPid)) { exit 0 } }
			  try {
			    $s = $mgr.GetCurrentSession()
			    if ($s -eq $null) { Emit '{"none":true}' }
			    else {
			      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			      if ($p -ne $null) {
			        $pb = $s.GetPlaybackInfo()
			        $tl = $s.GetTimelineProperties()
			        $key = "$($s.SourceAppUserModelId)|$($p.Title)|$($p.Artist)"
			        if ($key -ne $lastKey) { $lastKey = $key; $cover = ''; $coverTries = 0 }
			        $newCover = ''
			        if ($cover -eq '' -and $coverTries -lt 8 -and $p.Thumbnail -ne $null) {
			          $coverTries++
			          try {
			            $st = Await ($p.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
			            if ($st -ne $null -and $st.Size -gt 0 -and $st.Size -lt 8000000) {
			              $rd = New-Object Windows.Storage.Streams.DataReader($st)
			              [void](Await ($rd.LoadAsync([uint32]$st.Size)) ([uint32]))
			              $bytes = New-Object byte[] ([int]$st.Size)
			              $rd.ReadBytes($bytes)
			              $cover = [Convert]::ToBase64String($bytes)
			              $newCover = $cover
			              $rd.Dispose(); $st.Dispose()
			            }
			          } catch { Report ('cover: ' + $_.Exception.Message) }
			        }
			        $playing = ([string]$pb.PlaybackStatus -eq 'Playing')
			        $pos = $tl.Position.TotalSeconds
			        $dur = ($tl.EndTime - $tl.StartTime).TotalSeconds
			        if ($playing) { $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalSeconds }
			        if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
			        if ($pos -lt 0) { $pos = 0 }
			        $o = [ordered]@{ title = [string]$p.Title; artist = [string]$p.Artist; app = [string]$s.SourceAppUserModelId; playing = $playing; pos = [math]::Round($pos, 2); dur = [math]::Round($dur, 2) }
			        if ($newCover -ne '') { $o.cover = $newCover }
			        Emit (ConvertTo-Json $o -Compress)
			      }
			    }
			  } catch { Report ('loop: ' + $_.Exception.Message) }
			  Start-Sleep -Milliseconds 500
			}
