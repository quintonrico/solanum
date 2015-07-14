require 'solanum/matcher'

# This class represents a source of data, whether read from command output,
# a file on the system, or just calculated from other values.
#
# Author:: Greg Look
class Solanum::Source
  attr_reader :config, :matchers

  # Creates a new Source
  def initialize(config)
    @config = config
    @matchers = []
  end

  # Collects or generates metrics and returns a potentially updated metrics
  # map.
  def collect(metrics)
    metrics
  end

  private

  def process(lines, metrics)
    if lines.nil? || lines.empty?
      metrics
    else
      @matchers.reduce(metrics) do |m, matcher|
        matcher.call(m, lines)
      end
    end
  end

  # Declares a matcher for a single line of input.
  def match(pattern, options={}, &block)
    raise "pattern must be provided" if pattern.nil?

    commands = 0
    commands += 1 if options[:record]
    commands += 1 if block_given?
    raise "Must specify :record or provide a block to execute" if commands == 0
    raise "Only one of :record or a block should be provided" if commands > 1

    if options[:record]
      block = lambda do |metrics, matches|
        value = matches[1]
        value = value.send(options[:cast]) if options[:cast]
        value *= options[:scale] if options[:scale]

        metrics[options[:record]] = value
        metrics
      end
    end

    @matchers << Solanum::Matcher::LinePattern.new(block, pattern)
  end


  ### SOURCE TYPES ###

  public

  class Command < Solanum::Source
    def collect(metrics)
      # Locate absolute command path.
      command, args = @config.split(/\s/, 2)
      abs_command =
        if ::File.executable? command
          command
        else
          %x{which #{command} 2> /dev/null}.chomp
        end

      # Check, then execute command for line input.
      if not ::File.exist?(abs_command)
        raise "Command #{command} not found"
      elsif not ::File.executable?(abs_command)
        raise "Command #{abs_command} not executable"
      else
        lines = %x{#{abs_command} #{args}}.split("\n")
        raise "Error executing command: #{abs_command} #{args}" unless $?.success?
        process lines, metrics
      end
    end
  end

  class File < Solanum::Source
    def collect(metrics)
      raise "File does not exist: #{@config}" unless ::File.exists? @config
      raise "File is not readable: #{@config}" unless ::File.readable? @config

      # Read lines from the file.
      lines = ::File.open(@config) do |file|
        file.readlines
      end

      process lines, metrics
    end
  end

  class Compute < Solanum::Source
    def collect(metrics)
      # Compute metrics directly.
      @config.call(metrics)
    end
  end
end
