import asyncio
import logging
import io
import os
import pathlib
import requests
import subprocess
import sys
import time

from logging import Formatter, Logger
from logging.handlers import RotatingFileHandler
from typing import Any, Optional, Tuple

from python.agent_backchannel import (
    AgentBackchannel,
    AgentPorts,
    get_ledger_url,
)

AGENT_NAME = os.getenv("AGENT_NAME", "Agent")

# Setup logging ----------------------------------------------------------------

def logger() -> Logger:
    return logging.getLogger(__name__)

def init_logging(level='DEBUG', logfile=True) -> Logger:
    log = logging.getLogger(__name__)
    log.setLevel(level)

    ch = logging.StreamHandler()
    ch.setFormatter(Formatter(style='{'))
    ch.setLevel(logging.INFO)
    log.addHandler(ch)

    if logfile:
        fh = RotatingFileHandler('agent-debug.log', mode='w')
        fh.setFormatter(Formatter('{asctime} - {levelname:8} - [{processName}] - {message}', style='{'))
        fh.setLevel(logging.DEBUG)
        log.addHandler(fh)
    return log

# Camel Backchannel ------------------------------------------------------------

class CamelAgentBackchannel(AgentBackchannel):
    def __init__(self,
            ident: str,
            agent_ports: AgentPorts,
            genesis_data: str = None,
            params: dict = {},
            extra_args: dict = {}):
        super().__init__(ident, agent_ports, genesis_data, params, extra_args)
        with open("./acapy-version.txt", "r") as file:
            self.acapy_version = file.readline()

# Other stuff ------------------------------------------------------------------

def exception_message(ex: Exception):
    return f'{type(ex).__name__}: {ex}'

def read_process_stdout(name, proc):
    log = logger()
    for line in iter(proc.stdout.readline, b''):
        line = line.decode('utf-8').strip()
        log.info("[%s] %s", name, line)
    log.info("[%s] done reading stdout", name)

async def wrap(function):
    log = logger()
    try:
        return await function
    except Exception as ex:
        log.error(exception_message(ex), exc_info=1)
        raise ex

# Main entry point -------------------------------------------------------------

async def main(start_port: int):
    log = logger()

    agent_ports = AgentPorts(
        http=start_port + 1,
        admin=start_port + 2,
        # webhook=start_port + 3,
        ws=start_port + 2,
    )
    log.info("AgentPorts: %s", agent_ports)

    agent = CamelAgentBackchannel('camel', agent_ports)

    async def run_acapy_process(readiness_future):
        await agent.register_did()
        log.info("Starting: acapy process ...")
        proc = subprocess.Popen(
            ['./camel/bin/run-acapy.sh', 'start',
                '--label', f'camel.{AGENT_NAME}',
                '--endpoint', f'{agent.get_agent_endpoint("http")}',
                '--inbound-transport', 'http', '0.0.0.0', f'{agent_ports["http"]}',
                '--outbound-transport', 'http',
                '--admin', '0.0.0.0', f'{agent_ports["admin"]}',
                '--admin-insecure-mode',
                '--public-invites',
                '--wallet-name', f'{agent.wallet_name}',
                '--wallet-key', f'{agent.wallet_key}',
                '--wallet-type', f'{agent.wallet_type}',
                '--monitor-revocation-notification',
                # '--open-mediation',
                '--enable-undelivered-queue',
                '--auto-provision',
                '--recreate-wallet',
                '--genesis-url', f'{get_ledger_url()}/genesis',
                #'--genesis-transactions', f'{genesis_data}',
                '--seed', f'{agent.seed}',
                '--storage-type', f'{agent.storage_type}',
                #'--webhook-url', 'http://host.docker.internal:9023/webhooks',
                #'--tails-server-base-url', 'http://host.docker.internal:6543',
                #'--plugin', 'universal_resolver',
                #'--plugin-config', '/data-mount/plugin-config.yml',
                '--auto-accept-requests',
                '--log-level', os.getenv("LOG_LEVEL", "info")],
                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

        # Start the stdout reaper thread
        coro_stdout = asyncio.to_thread(read_process_stdout, "acapy", proc)

        async def query_features(readiness_future):
            status_code = count = 0
            while count < 40 and status_code != 200:
                count += 1
                try:
                    adminUrl = f'http://{agent.internal_host}:{agent_ports["admin"]}'
                    req = requests.get(f'{adminUrl}/discover-features/query')
                    status_code = req.status_code
                except Exception as ex:
                    await asyncio.sleep(0.5)
                    continue
            result = (status_code, req.json())
            readiness_future.set_result(result)

        # Query ACA-Py features, which serves as readiness check
        await query_features(readiness_future)
        await coro_stdout

        log.info("Stopped: acapy process")

    async def run_camel_process():
        log.info("Starting: camel process ...")
        proc = subprocess.Popen(
            ['./camel/bin/run-camel.sh',
                '--port', str(start_port),
                '--wallet-name', f'{agent.wallet_name}',
                '--wallet-key', f'{agent.wallet_key}',
                '--wallet-type', f'{agent.wallet_type}',
                '--admin-endpoint', f'http://{agent.internal_host}:{agent_ports["admin"]}',
                '--user-endpoint', f'http://{agent.internal_host}:{agent_ports["http"]}',
                '--ws-endpoint', f'ws://{agent.internal_host}:{agent_ports["ws"]}/ws'],
            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        await asyncio.to_thread(read_process_stdout, "camel", proc)
        log.info("Stopped: camel process")

    # Start ACA-Py process
    readiness_future = asyncio.Future()
    asyncio.create_task(wrap(run_acapy_process(readiness_future)))

    await readiness_future
    (code, jobj) = readiness_future.result()
    assert code == 200, "Unexpected satus code"

    # Show ACA-Py features
    protocols = jobj['disclose']['protocols'];
    log.info(f"ACA-Py ready with {len(protocols)} supported features")

    # Start Camel process
    asyncio.create_task(wrap(run_camel_process()))

    # now wait ...
    print("Press Ctrl-C to exit ...")
    remaining_tasks = asyncio.all_tasks()
    await asyncio.gather(*remaining_tasks)

if __name__ == "__main__":
    import argparse

    log = init_logging()

    parser = argparse.ArgumentParser(description="Run a Camel agent")
    parser.add_argument(
        "-p",
        "--port",
        type=int,
        default=8020,
        metavar=("<port>"),
        help="Choose the starting port number to listen on",
    )
    args = parser.parse_known_args()[0]
    log.info("args: %s", args)

    try:
        asyncio.run(main(start_port=args.port))
    except KeyboardInterrupt:
        os.exit(1)
