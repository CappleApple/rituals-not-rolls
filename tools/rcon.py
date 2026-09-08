"""Local validation RCON client: python tools/rcon.py 'ritual validate' 'reload' 'stop'."""
import socket,struct,sys
def readn(s,n):
    out=b''
    while len(out)<n:
        part=s.recv(n-len(out))
        if not part:raise EOFError('RCON connection closed')
        out+=part
    return out
def packet(s,kind,text,number):
    body=struct.pack('<ii',number,kind)+text.encode()+b'\0\0';s.sendall(struct.pack('<i',len(body))+body)
    size=struct.unpack('<i',readn(s,4))[0];data=readn(s,size)
    return struct.unpack('<ii',data[:8]),data[8:-2].decode(errors='replace')
with socket.create_connection(('127.0.0.1',25586),timeout=15) as s:
    info,_=packet(s,3,'ritual-local-validation',1)
    if info[0]<0:raise SystemExit('RCON authentication failed')
    for i,command in enumerate(sys.argv[1:],2):
        try: print(command+': '+packet(s,2,command,i)[1])
        except EOFError:
            if command!='stop':raise
