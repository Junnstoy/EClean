import concurrent.futures, os, pathlib, subprocess, urllib.parse
from importlib.machinery import SourceFileLoader
ROOT=pathlib.Path(__file__).resolve().parent.parent/".integration-work"
VERSIONS={'1.8.8':8,'1.12.2':8,'1.13.2':8,'1.16.5':8,'1.17.1':17,'1.18.2':17,'1.20.4':17,'1.20.6':21,'1.21.11':21,'26.3':25}
def java_home(major):
 override=os.environ.get(f'ECLEAN_JAVA_{major}_HOME')
 return pathlib.Path(override) if override else next((ROOT/f'runtime-tools/java{major}').glob('*/bin/java')).parent.parent
def java_args(v):
 java=java_home(VERSIONS[v])/'bin/java'
 args=[str(java),'-Xms256M','-Xmx1200M','-Djava.awt.headless=true','-Dterminal.jline=false','-Dterminal.ansi=false','-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts','-DIReallyKnowWhatIAmDoingISwear=true']
 proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
 if proxy.hostname:args += [f'-Dhttps.proxyHost={proxy.hostname}',f'-Dhttps.proxyPort={proxy.port}',f'-Dhttp.proxyHost={proxy.hostname}',f'-Dhttp.proxyPort={proxy.port}']
 return args
def prepare(v):
 p=ROOT/'runtime-tools/servers'/v;(p/'eula.txt').write_text('eula=false\n')
 with (p/'prepare.log').open('w') as log:
  r=subprocess.run(java_args(v)+['-Dpaperclip.patchonly=true','-jar','paper.jar'],cwd=p,stdout=log,stderr=subprocess.STDOUT,timeout=280)
 if r.returncode:raise RuntimeError(v)
 print('PREPARED',v,flush=True)
if __name__=='__main__':
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
  for f in concurrent.futures.as_completed([pool.submit(prepare,v) for v in VERSIONS]):f.result()
