"""Minimal native structure NBT fixtures; large rooms keep 16-block networks isolated between GameTests."""
from pathlib import Path
import gzip,struct
def string(s):b=s.encode();return struct.pack('>H',len(b))+b
def named(tag,name,payload):return bytes([tag])+string(name)+payload
def integer(i):return struct.pack('>i',i)
def listtag(name,kind,values):return named(9,name,bytes([kind])+integer(len(values))+b''.join(values))
for name,size in [('empty',[5,4,5]),('network',[40,5,40])]:
    payload=named(3,'DataVersion',integer(3955))+listtag('size',3,[integer(i) for i in size])+listtag('palette',10,[named(8,'Name',string('minecraft:air'))+b'\0'])+listtag('blocks',10,[])+listtag('entities',10,[])+b'\0'
    path=Path('src/main/resources/data/ritualsnotrolls/structure')/(name+'.nbt');path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(gzip.compress(b'\x0a\0\0'+payload,mtime=0))
